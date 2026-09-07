package net.nmoncho.faradn.document;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.Utils;

/**
 * Builds the IR ({@code List<Block>}) from a jsoup document.
 * <p>
 * Traverses the body with an explicit style stack: entering an element
 * pushes the combined {@link ComputedStyle}, leaving it pops, so every text
 * node picks up the fully resolved style in effect at its position. Block
 * boundaries (block-level tags, {@code br}, images, barcodes, rules) flush
 * the pending inline runs into a {@link Paragraph}.
 * <p>
 * Whitespace is normalized here, HTML-style: runs of whitespace collapse to
 * a single space, and spaces at block boundaries are dropped. A collapsed
 * space between two runs is attached to the preceding run so styled runs do
 * not start with invisible styled characters.
 */
public final class BlockBuilder implements org.jsoup.select.NodeVisitor {

  private static final Set<String> BLOCK_TAGS = Set.of("body", "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
      "ul", "ol", "li", "blockquote", "header", "footer", "section", "article", "main", "nav", "aside", "center",
      "table", "tr", "pre", "address", "figure", "figcaption");

  private static final String BARCODE_TAG = "bar-code";
  private static final String BARCODE_CLASS_PREFIX = "bar-code--";

  private final List<Block> blocks = new ArrayList<>();
  private final List<TextRun> runs = new ArrayList<>();
  private final Deque<ComputedStyle> styles = new ArrayDeque<>();

  private boolean pendingSpace = false;
  private Element consumedSubtree = null;
  private final Deque<ListState> lists = new ArrayDeque<>();
  private String pendingMarker = null;
  private int preDepth = 0;
  private final int dpi;

  private BlockBuilder(int dpi) {
    this.dpi = dpi;
    styles.push(ComputedStyle.INITIAL);
  }

  /**
   * Builds the block sequence for a document.
   *
   * @param doc
   *        parsed document to translate
   * @param dpi
   *        printer resolution, used to resolve physical CSS lengths
   *        ({@code mm}/{@code cm}) in page-mode layouts to dots
   * @return immutable list of blocks, in reading order
   */
  public static List<Block> build(org.jsoup.nodes.Document doc, int dpi) {
    final BlockBuilder builder = new BlockBuilder(dpi);
    final Element body = doc.body();

    // A sized <body> turns the whole job into a single page-mode label area
    // (fixed-size badge / ticket) instead of the standard-mode receipt flow.
    final Optional<Canvas> label = builder.labelBody(body);
    if (label.isPresent()) {
      return List.of(label.get());
    }

    body.traverse(builder);
    builder.flushParagraph();

    return List.copyOf(builder.blocks);
  }

  /**
   * Treats a {@code <body>} with an explicit, positive {@code width} and
   * {@code height} as one whole-job {@link Canvas}: its
   * {@code position: absolute}
   * children become placements (like any page-mode container, but spanning the
   * entire document). Empty when the body carries no size.
   */
  private Optional<Canvas> labelBody(Element body) {
    if (canvasSize(body).isEmpty()) {
      return Optional.empty();
    }
    return buildCanvas(body, ComputedStyle.INITIAL.process(body));
  }

  @Override
  public void head(Node node, int depth) {
    if (node instanceof TextNode text) {
      appendText(text);
      return;
    }

    if (!(node instanceof Element el) || consumedSubtree != null) {
      return;
    }

    styles.push(styles.peek().process(el));

    final String tag = el.normalName();
    if (isBarcode(el)) {
      flushParagraph();
      barcodeData(el).ifPresent(data -> blocks
          .add(new Barcode(
              data,
              barcodeSymbology(el).orElse(null),
              styles.peek().alignment(),
              barcodeOptions(el))));
      consumedSubtree = el;
    } else if (tag.equals("table")) {
      flushParagraph();
      buildTable(el, styles.peek()).ifPresent(blocks::add);
      consumedSubtree = el;
    } else if (tag.equals("img")) {
      flushParagraph();
      blocks.add(new ImageBlock(Image.fromNode(el), styles.peek().alignment()));
    } else if (tag.equals("hr")) {
      flushParagraph();
      blocks.add(new Rule());
    } else if (isCanvasContainer(el)) {
      flushParagraph();
      buildCanvas(el, styles.peek()).ifPresent(blocks::add);
      consumedSubtree = el;
    } else if (tag.equals("ul") || tag.equals("ol")) {
      flushParagraph();
      lists.push(new ListState(tag.equals("ol")));
    } else if (tag.equals("li")) {
      flushParagraph();
      final String marker = listMarker();
      pendingMarker = marker.isEmpty() ? null : marker;
    } else if (tag.equals("pre")) {
      flushParagraph();
      preDepth++;
    } else if (tag.equals("br") || BLOCK_TAGS.contains(tag)) {
      flushParagraph();
    }
  }

  @Override
  public void tail(Node node, int depth) {
    if (!(node instanceof Element el)) {
      return;
    }

    if (consumedSubtree != null) {
      if (consumedSubtree == el) {
        consumedSubtree = null;
        styles.pop();
      }
      return;
    }

    styles.pop();

    final String tag = el.normalName();
    if (tag.equals("ul") || tag.equals("ol")) {
      if (!lists.isEmpty()) {
        lists.pop();
      }
    } else if (tag.equals("pre") && preDepth > 0) {
      preDepth--;
    }

    if (BLOCK_TAGS.contains(tag)) {
      flushParagraph();
    }
  }

  private void appendText(TextNode text) {
    if (consumedSubtree != null) {
      return;
    }

    if (preDepth > 0) {
      // Preformatted: preserve whitespace, and break the block on each newline.
      emitPendingMarker();
      final String[] lines = text.getWholeText().split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        if (i > 0) {
          flushParagraph();
        }
        if (!lines[i].isEmpty()) {
          addRun(lines[i], styles.peek());
        }
      }
      return;
    }

    final String collapsed = text.getWholeText().replaceAll("\\s+", " ");
    if (collapsed.isEmpty()) {
      return;
    }

    final String core = collapsed.strip();
    if (core.isEmpty()) {
      // Whitespace-only node: a separator if inline content is pending
      pendingSpace = pendingSpace || !runs.isEmpty();
      return;
    }

    if ((pendingSpace || collapsed.startsWith(" ")) && !runs.isEmpty()) {
      final TextRun last = runs.remove(runs.size() - 1);
      runs.add(new TextRun(last.text() + " ", last.style()));
    }

    emitPendingMarker();
    addRun(core, styles.peek());
    pendingSpace = collapsed.endsWith(" ");
  }

  /** Emits a pending list marker as the first run of the current item. */
  private void emitPendingMarker() {
    if (pendingMarker != null) {
      addRun(pendingMarker, styles.peek());
      pendingMarker = null;
    }
  }

  private void addRun(String text, ComputedStyle style) {
    // Merge with the previous run when the style is unchanged
    if (!runs.isEmpty() && runs.get(runs.size() - 1).style().equals(style)) {
      final TextRun last = runs.remove(runs.size() - 1);
      runs.add(new TextRun(last.text() + text, style));
    } else {
      runs.add(new TextRun(text, style));
    }
  }

  private void flushParagraph() {
    pendingSpace = false;
    if (runs.isEmpty()) {
      return;
    }

    // Block boundary: trailing spaces do not survive
    final TextRun last = runs.remove(runs.size() - 1);
    final String trimmed = last.text().stripTrailing();
    if (!trimmed.isEmpty()) {
      runs.add(new TextRun(trimmed, last.style()));
    }

    if (!runs.isEmpty()) {
      blocks.add(new Paragraph(List.copyOf(runs), runs.get(0).style().alignment()));
    }
    runs.clear();
  }

  private static boolean isBarcode(Element el) {
    return el.normalName().equals(BARCODE_TAG) || el.hasClass(BARCODE_TAG);
  }

  private static Optional<String> barcodeData(Element el) {
    final String attr = el.attr("data");
    final String data = attr.isBlank() ? el.text().strip() : attr.strip();

    return data.isEmpty() ? Optional.empty() : Optional.of(data);
  }

  private static Optional<String> barcodeSymbology(Element el) {
    final String attr = el.attr("symbology");
    if (!attr.isBlank()) {
      return Optional.of(attr.strip());
    }

    return el
        .classNames()
        .stream()
        .filter(name -> name.startsWith(BARCODE_CLASS_PREFIX))
        .map(name -> name.substring(BARCODE_CLASS_PREFIX.length()))
        .filter(name -> !name.isBlank())
        .findFirst();
  }

  private static BarcodeOptions barcodeOptions(Element el) {
    final int height = intAttr(el, "height", BarcodeOptions.DEFAULT.heightDots());
    final int module = intAttr(el, "module", BarcodeOptions.DEFAULT.moduleSize());
    // Clamp rather than reject: HTML attributes are lenient input.
    return new BarcodeOptions(clamp(height, 1, 255), clamp(module, 0, 16),
        hriOf(el.attr("hri")), qrEcOf(el.attr("ec")));
  }

  private static int intAttr(Element el, String name, int fallback) {
    final String raw = el.attr(name).strip();
    if (raw.isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static BarcodeOptions.Hri hriOf(String raw) {
    return switch (raw.strip().toLowerCase()) {
      case "none", "off" -> BarcodeOptions.Hri.NONE;
      case "above", "top" -> BarcodeOptions.Hri.ABOVE;
      case "both" -> BarcodeOptions.Hri.BOTH;
      default -> BarcodeOptions.Hri.BELOW;
    };
  }

  private static BarcodeOptions.QrEc qrEcOf(String raw) {
    return switch (raw.strip().toLowerCase()) {
      case "l" -> BarcodeOptions.QrEc.L;
      case "q" -> BarcodeOptions.QrEc.Q;
      case "h" -> BarcodeOptions.QrEc.H;
      default -> BarcodeOptions.QrEc.M;
    };
  }

  /**
   * A page-mode container: a {@code position: relative} (or {@code absolute})
   * element with an explicit, positive {@code width} and {@code height}. Its
   * {@code position: absolute} children become {@link Placement}s in a
   * {@link Canvas} (see {@link #buildCanvas}).
   */
  private boolean isCanvasContainer(Element el) {
    final Optional<String> position = Utils.findStyleValue(el, "position");
    if (position.isEmpty()) {
      return false;
    }
    final String p = position.get().strip().toLowerCase();
    if (!p.equals("relative") && !p.equals("absolute")) {
      return false;
    }
    return canvasSize(el).isPresent();
  }

  /**
   * The container's {@code [widthDots, heightDots]} when both are present and
   * positive.
   */
  private Optional<int[]> canvasSize(Element el) {
    final OptionalInt width = styleLength(el, "width", 0);
    final OptionalInt height = styleLength(el, "height", 0);
    if (width.isPresent() && height.isPresent() && width.getAsInt() > 0 && height.getAsInt() > 0) {
      return Optional.of(new int[] { width.getAsInt(), height.getAsInt() });
    }
    return Optional.empty();
  }

  /**
   * Resolves a CSS length property on an element to dots, if present and valid.
   */
  private OptionalInt styleLength(Element el, String property, int referenceDots) {
    final Optional<String> raw = Utils.findStyleValue(el, property);
    return raw.isPresent() ? Utils.lengthToDots(raw.get(), dpi, referenceDots) : OptionalInt.empty();
  }

  /**
   * Translates a sized, positioned container into a {@link Canvas}: each
   * {@code position: absolute} child is placed at its {@code left}/{@code top}
   * (dots from the top-left, {@code %} relative to the area, missing → 0).
   * Non-positioned children are ignored in this version; rotation is not yet
   * mapped (always {@link Canvas.Direction#NORMAL}).
   */
  private Optional<Canvas> buildCanvas(Element container, ComputedStyle base) {
    final Optional<int[]> size = canvasSize(container);
    if (size.isEmpty()) {
      return Optional.empty();
    }
    final int widthDots = size.get()[0];
    final int heightDots = size.get()[1];

    final List<Placement> placements = new ArrayList<>();
    for (Element child : container.children()) {
      if (!isAbsolutelyPositioned(child)) {
        Utils.log.debug("Ignoring non-absolutely-positioned <{}> in page-mode container", child.normalName());
        continue;
      }
      final ComputedStyle childStyle = base.process(child);
      final int x = Math.max(0, styleLength(child, "left", widthDots).orElse(0));
      final int y = Math.max(0, styleLength(child, "top", heightDots).orElse(0));
      placeableOf(child, childStyle).ifPresent(content -> placements.add(new Placement(x, y, content)));
    }
    return Optional.of(new Canvas(widthDots, heightDots, directionOf(container), placements));
  }

  /**
   * {@code transform: rotate(90|180|270deg)} on the container becomes a canvas
   * {@link Canvas.Direction}.
   */
  private static final Pattern ROTATE = Pattern.compile(
      "rotate\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:deg)?\\s*\\)", Pattern.CASE_INSENSITIVE);

  private static Canvas.Direction directionOf(Element container) {
    final Optional<String> transform = Utils.findStyleValue(container, "transform");
    if (transform.isEmpty()) {
      return Canvas.Direction.NORMAL;
    }
    final Matcher matcher = ROTATE.matcher(transform.get());
    if (!matcher.find()) {
      return Canvas.Direction.NORMAL;
    }
    // Snap to the nearest right angle and normalize to [0, 360). CSS rotation is
    // clockwise, so 90deg = a clockwise quarter turn.
    final int degrees = ((int) Math.round(Double.parseDouble(matcher.group(1)) / 90.0) * 90 % 360 + 360) % 360;
    return switch (degrees) {
      case 90 -> Canvas.Direction.ROTATE_90_CW;
      case 180 -> Canvas.Direction.ROTATE_180;
      case 270 -> Canvas.Direction.ROTATE_90_CCW;
      default -> Canvas.Direction.NORMAL;
    };
  }

  private static boolean isAbsolutelyPositioned(Element el) {
    return Utils.findStyleValue(el, "position")
        .map(p -> p.strip().equalsIgnoreCase("absolute"))
        .orElse(false);
  }

  /**
   * Maps a positioned child element to the content it holds: a barcode, an
   * image, or otherwise a paragraph of its inline text. Empty when the child has
   * no printable content.
   */
  private static Optional<Placeable> placeableOf(Element el, ComputedStyle style) {
    if (isBarcode(el)) {
      return barcodeData(el).<Placeable>map(data -> new Barcode(
          data, barcodeSymbology(el).orElse(null), style.alignment(), barcodeOptions(el)));
    }
    if (el.normalName().equals("img")) {
      return Optional.of(new ImageBlock(Image.fromNode(el), style.alignment()));
    }
    final List<TextRun> runs = cellContent(el, style);
    return runs.isEmpty() ? Optional.empty() : Optional.of(new Paragraph(runs, style.alignment()));
  }

  private static Optional<Table> buildTable(Element table, ComputedStyle base) {
    final List<List<Cell>> rows = new ArrayList<>();
    for (Element tr : table.select("tr")) {
      final List<Cell> cells = new ArrayList<>();
      for (Element cell : tr.children()) {
        final String tag = cell.normalName();
        if (!tag.equals("td") && !tag.equals("th")) {
          continue;
        }
        final ComputedStyle cellStyle = tag.equals("th") ? asHeader(base.process(cell)) : base.process(cell);
        cells.add(new Cell(cellContent(cell, cellStyle), cellStyle.alignment(), colSpan(cell)));
      }
      if (!cells.isEmpty()) {
        rows.add(cells);
      }
    }
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    final Border border = tableBorder(table);
    return Optional.of(new Table(rows, border, border.any()));
  }

  /**
   * The border for a table from its {@code <table border>} attribute or CSS
   * {@code border}/{@code border-style}. A single-line grid by default; a double
   * grid for {@code border-style: double}; {@link Border#NONE} when absent or
   * explicitly {@code none}/{@code 0}.
   */
  private static Border tableBorder(Element table) {
    final String attr = table.attr("border").strip();
    final Optional<String> css = Utils.findStyleValue(table, "border").map(v -> v.toLowerCase());
    final Optional<String> cssStyle = Utils.findStyleValue(table, "border-style").map(v -> v.toLowerCase());

    // An explicit CSS "none"/"0" turns borders off, even if the attribute is set.
    if (css.map(v -> v.equals("none") || v.equals("0")).orElse(false)
        || cssStyle.map(v -> v.equals("none")).orElse(false)) {
      return Border.NONE;
    }

    final boolean bordered = (!attr.isEmpty() && !attr.equals("0")) || css.isPresent() || cssStyle.isPresent();
    if (!bordered) {
      return Border.NONE;
    }

    final boolean isDouble = css.map(v -> v.contains("double")).orElse(false)
        || cssStyle.map(v -> v.contains("double")).orElse(false);
    return Border.all(isDouble ? Border.Style.DOUBLE : Border.Style.SINGLE);
  }

  /**
   * {@code
   *
  <th>} is bold; the rest of the cell's style carries through.
   */
  private static ComputedStyle asHeader(ComputedStyle style) {
    return new ComputedStyle(true, style.underline(), style.widthMultiple(), style.heightMultiple(),
        style.alignment(), style.invert(), style.font(), style.italic());
  }

  private static int colSpan(Element cell) {
    final String raw = cell.attr("colspan").strip();
    if (raw.isEmpty()) {
      return 1;
    }
    try {
      return Math.max(1, Integer.parseInt(raw));
    } catch (NumberFormatException ignored) {
      return 1;
    }
  }

  /**
   * Builds a cell's content as styled runs, preserving inline spans
   * ({@code <b>}, {@code <u>}, …) with the same whitespace collapsing and
   * run-merging as paragraphs. Structural children are flattened; {@code <br>
   * }
   * becomes a space.
   */
  private static List<TextRun> cellContent(Element cell, ComputedStyle cellStyle) {
    final List<TextRun> runs = new ArrayList<>();
    collectInline(cell, cellStyle, runs, new boolean[] { false });
    if (!runs.isEmpty()) {
      final TextRun last = runs.remove(runs.size() - 1);
      final String trimmed = last.text().stripTrailing();
      if (!trimmed.isEmpty()) {
        runs.add(new TextRun(trimmed, last.style()));
      }
    }
    return List.copyOf(runs);
  }

  private static void collectInline(Node node, ComputedStyle style, List<TextRun> runs, boolean[] pendingSpace) {
    for (Node child : node.childNodes()) {
      if (child instanceof TextNode text) {
        appendInline(text.getWholeText(), style, runs, pendingSpace);
      } else if (child instanceof Element el) {
        if (el.normalName().equals("br")) {
          pendingSpace[0] = pendingSpace[0] || !runs.isEmpty();
        } else {
          collectInline(el, style.process(el), runs, pendingSpace);
        }
      }
    }
  }

  private static void appendInline(String raw, ComputedStyle style, List<TextRun> runs, boolean[] pendingSpace) {
    final String collapsed = raw.replaceAll("\\s+", " ");
    if (collapsed.isEmpty()) {
      return;
    }
    final String core = collapsed.strip();
    if (core.isEmpty()) {
      pendingSpace[0] = pendingSpace[0] || !runs.isEmpty();
      return;
    }
    if ((pendingSpace[0] || collapsed.startsWith(" ")) && !runs.isEmpty()) {
      final TextRun last = runs.remove(runs.size() - 1);
      runs.add(new TextRun(last.text() + " ", last.style()));
    }
    if (!runs.isEmpty() && runs.get(runs.size() - 1).style().equals(style)) {
      final TextRun last = runs.remove(runs.size() - 1);
      runs.add(new TextRun(last.text() + core, style));
    } else {
      runs.add(new TextRun(core, style));
    }
    pendingSpace[0] = collapsed.endsWith(" ");
  }

  /**
   * The marker for the current list item: {@code "N. "} for ordered lists,
   * {@code "- "} otherwise.
   */
  private String listMarker() {
    if (lists.isEmpty()) {
      return "";
    }
    final ListState top = lists.peek();
    final String indent = "  ".repeat(Math.max(0, lists.size() - 1));
    if (top.ordered) {
      top.counter++;
      return indent + top.counter + ". ";
    }
    return indent + "- ";
  }

  private static final class ListState {
    private final boolean ordered;
    private int counter;

    private ListState(boolean ordered) {
      this.ordered = ordered;
    }
  }
}
