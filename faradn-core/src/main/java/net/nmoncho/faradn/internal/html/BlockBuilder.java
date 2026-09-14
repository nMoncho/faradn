//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.internal.html;

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
import net.nmoncho.faradn.PrintingException;
import net.nmoncho.faradn.Utils;
import net.nmoncho.faradn.document.*;

/**
 * Builds the IR ({@code List<Block>}) from a jsoup document.
 * <p>
 * <strong>Not public API.</strong> This lives in an internal package so jsoup
 * does not leak into the exported surface; reach the IR through
 * {@link net.nmoncho.faradn.Document#blocks()} instead.
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
  private static final String CASH_DRAWER_TAG = "cash-drawer";
  private static final String CUT_TAG = "cut";
  private static final String FEED_TAG = "feed";

  // The current block accumulator. Normally the root output list, but while
  // inside a bordered container it is that box's child list (see boxes).
  private List<Block> blocks = new ArrayList<>();
  // The current inline-run accumulator. Normally the paragraph's runs, but while
  // inside a `float: right` span it is that span's (right-group) runs.
  private List<TextRun> runs = new ArrayList<>();
  private final Deque<ComputedStyle> styles = new ArrayDeque<>();
  private final Deque<BoxFrame> boxes = new ArrayDeque<>();

  // A `float: right` span in progress: the element that opened it, the left
  // group's runs (parked while the span accumulates), and the leader fill.
  // `pendingRight` holds the captured right group until the block flushes into a
  // LeaderLine.
  private Element floatOpener = null;
  private List<TextRun> leftRuns = null;
  private char leaderFill = ' ';
  private List<TextRun> pendingRight = null;

  private boolean pendingSpace = false;
  private Element consumedSubtree = null;
  private final Deque<ListState> lists = new ArrayDeque<>();
  private String pendingMarker = null;
  // Marker widths of the open list items, so a wrapped item hangs its
  // continuation lines under the text (a hanging indent).
  private final Deque<Integer> liMarkers = new ArrayDeque<>();
  private int preDepth = 0;
  private final int dpi;

  /**
   * An open bordered container: the element that opened it, its border, and the
   * parent accumulator to restore.
   */
  private record BoxFrame(Element opener, Border border, List<Block> parent) {
  }

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
    return buildCanvas(body, StyleResolver.resolve(ComputedStyle.INITIAL, body));
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

    styles.push(StyleResolver.resolve(styles.peek(), el));

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
      blocks.add(new ImageBlock(imageFrom(el), styles.peek().alignment()));
    } else if (tag.equals("hr")) {
      flushParagraph();
      blocks.add(new Rule());
    } else if (tag.equals(CASH_DRAWER_TAG)) {
      flushParagraph();
      blocks.add(new Drawer(drawerPin(el)));
      consumedSubtree = el;
    } else if (tag.equals(CUT_TAG)) {
      flushParagraph();
      blocks.add(new Cut(cutIsPartial(el)));
      consumedSubtree = el;
    } else if (tag.equals(FEED_TAG)) {
      flushParagraph();
      blocks.add(new Feed(feedLines(el)));
      consumedSubtree = el;
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
      liMarkers.push(marker.length());
    } else if (tag.equals("pre")) {
      flushParagraph();
      preDepth++;
    } else if (tag.equals("br") || BLOCK_TAGS.contains(tag)) {
      flushParagraph();
    }

    // A bordered block container captures the blocks produced inside it, so its
    // border can frame the whole group at tail (see closeBox). The prior content
    // was already flushed above into the parent accumulator.
    if (consumedSubtree == null && BLOCK_TAGS.contains(tag)) {
      final int marginTop = verticalSpaceDots(el, "margin-top");
      if (marginTop > 0) {
        blocks.add(new Space(marginTop)); // margin is outside the box (added before the frame)
      }
      final Border border = blockBorder(el);
      if (border.any()) {
        boxes.push(new BoxFrame(el, border, blocks));
        blocks = new ArrayList<>();
      }
      final int paddingTop = verticalSpaceDots(el, "padding-top");
      if (paddingTop > 0) {
        blocks.add(new Space(paddingTop)); // padding is inside the box (added after the frame)
      }
    }

    // A `float: right` span splits the line: park the left group and accumulate
    // the span's content as the right group (closed in tail, flushed as a
    // LeaderLine). Only the first float per line is handled.
    if (consumedSubtree == null && floatOpener == null && pendingRight == null && isFloatRight(el)) {
      openFloat(el);
    }
  }

  private static boolean isFloatRight(Element el) {
    return HtmlUtil.findStyleValue(el, "float").map(v -> v.strip().equalsIgnoreCase("right")).orElse(false);
  }

  private void openFloat(Element el) {
    // Keep the pending space so a dotted leader reads "Subtotal ....." not "Subtotal.....".
    if (pendingSpace && !runs.isEmpty()) {
      final TextRun last = runs.remove(runs.size() - 1);
      runs.add(new TextRun(last.text() + " ", last.style()));
    }
    pendingSpace = false;
    leftRuns = runs;
    runs = new ArrayList<>();
    floatOpener = el;
    leaderFill = leaderFillOf(el);
  }

  /**
   * The fill character for a leader: the first char of {@code data-leader}, or a
   * space.
   */
  private static char leaderFillOf(Element el) {
    final String value = el.attr("data-leader");
    return value.isEmpty() ? ' ' : value.charAt(0);
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

    if (floatOpener == el) {
      closeFloat();
    }

    final String tag = el.normalName();
    if (tag.equals("ul") || tag.equals("ol")) {
      if (!lists.isEmpty()) {
        lists.pop();
      }
    } else if (tag.equals("pre") && preDepth > 0) {
      preDepth--;
    }

    if (BLOCK_TAGS.contains(tag)) {
      flushParagraph(blockLayout(el)); // this block's direct inline content, with its indentation
      final int paddingBottom = verticalSpaceDots(el, "padding-bottom");
      if (paddingBottom > 0) {
        blocks.add(new Space(paddingBottom)); // padding is inside the box (before closeBox)
      }
      if (!boxes.isEmpty() && boxes.peek().opener() == el) {
        closeBox();
      }
      final int marginBottom = verticalSpaceDots(el, "margin-bottom");
      if (marginBottom > 0) {
        blocks.add(new Space(marginBottom)); // margin is outside the box (after closeBox)
      }
    }
    if (tag.equals("li") && !liMarkers.isEmpty()) {
      liMarkers.pop();
    }
  }

  /**
   * A block's vertical margin or padding in dots
   * ({@code px}/{@code mm}/{@code cm}
   * from the longhand or the {@code margin}/{@code padding} shorthand), clamped
   * to
   * a byte.
   */
  private int verticalSpaceDots(Element el, String property) {
    final Optional<String> value = boxSide(el, property);
    if (value.isEmpty()) {
      return 0;
    }
    final OptionalInt dots = Utils.lengthToDots(value.get(), dpi, 0);
    return dots.isPresent() ? Math.max(0, Math.min(255, dots.getAsInt())) : 0;
  }

  /**
   * The value for one side of a box property: the {@code <prop>-<side>} longhand
   * if set, otherwise the matching side of the {@code <prop>} shorthand (1-4
   * values in CSS order top/right/bottom/left).
   */
  private static Optional<String> boxSide(Element el, String longhand) {
    final Optional<String> direct = HtmlUtil.findStyleValue(el, longhand);
    if (direct.isPresent()) {
      return direct;
    }
    final int dash = longhand.indexOf('-');
    final String shorthand = longhand.substring(0, dash);
    final String side = longhand.substring(dash + 1);
    return HtmlUtil.findStyleValue(el, shorthand).map(value -> shorthandSide(value, side));
  }

  private static String shorthandSide(String value, String side) {
    final String[] parts = value.strip().split("\\s+");
    final String top = parts[0];
    final String right = parts.length > 1 ? parts[1] : parts[0];
    final String bottom = parts.length > 2 ? parts[2] : parts[0];
    final String left = parts.length > 3 ? parts[3] : right;
    return switch (side) {
      case "top" -> top;
      case "right" -> right;
      case "bottom" -> bottom;
      default -> left;
    };
  }

  /**
   * Closes the bordered container opened by the current element: pops its
   * captured children, restores the parent accumulator, and adds a {@link Box}
   * (or,
   * for a lone borderless paragraph, that paragraph with the border attached -
   * the
   * single-paragraph case that {@code Paragraph}'s border covers).
   */
  private void closeBox() {
    final BoxFrame frame = boxes.pop();
    final List<Block> children = blocks;
    blocks = frame.parent();
    if (children.isEmpty()) {
      return;
    }
    if (children.size() == 1 && children.get(0) instanceof Paragraph only && only.border().equals(Border.NONE)) {
      blocks.add(new Paragraph(only.runs(), only.alignment(), frame.border(), only.layout(), only.filled()));
    } else {
      blocks.add(new Box(frame.border(), children));
    }
  }

  /**
   * The border for a block-level element from CSS {@code border-<side>} or the
   * {@code border}/{@code border-style} shorthand. All four sides are recorded
   * (double for a {@code double} style); the renderer draws only top/bottom for
   * now. {@link Border#NONE} when absent or {@code none}/{@code 0}.
   */
  private static Border blockBorder(Element el) {
    final boolean top = sideBordered(el, "border-top");
    final boolean right = sideBordered(el, "border-right");
    final boolean bottom = sideBordered(el, "border-bottom");
    final boolean left = sideBordered(el, "border-left");
    if (!(top || right || bottom || left)) {
      return Border.NONE;
    }
    return new Border(top, right, bottom, left, mentionsDouble(el) ? Border.Style.DOUBLE : Border.Style.SINGLE);
  }

  /**
   * Whether a side has a border: its own {@code border-<side>}, else the
   * {@code border}/{@code border-style} shorthand.
   */
  private static boolean sideBordered(Element el, String sideProperty) {
    final Optional<String> side = HtmlUtil.findStyleValue(el, sideProperty);
    if (side.isPresent()) {
      return !isNoneBorder(side.get());
    }
    return HtmlUtil.findStyleValue(el, "border").map(v -> !isNoneBorder(v)).orElse(false)
        || HtmlUtil.findStyleValue(el, "border-style").map(v -> !isNoneBorder(v)).orElse(false);
  }

  private static boolean isNoneBorder(String value) {
    final String v = value.strip().toLowerCase();
    return v.equals("none") || v.equals("0") || v.equals("hidden");
  }

  private static boolean mentionsDouble(Element el) {
    for (String property : new String[] { "border", "border-style", "border-top", "border-right", "border-bottom",
        "border-left" }) {
      if (HtmlUtil.findStyleValue(el, property).map(v -> v.toLowerCase().contains("double")).orElse(false)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The indentation for a block-level element: {@code margin-left + padding-left}
   * and their right counterparts, plus {@code text-indent} for the first line
   * (all in character columns). A {@code
   *
  <li>} adds a hanging indent equal to its
   * marker width, so wrapped lines align under the text rather than the marker.
   */
  private BlockLayout blockLayout(Element el) {
    int left = indentColumns(el, "margin-left") + indentColumns(el, "padding-left");
    int right = indentColumns(el, "margin-right") + indentColumns(el, "padding-right");
    int firstLine = signedColumns(HtmlUtil.findStyleValue(el, "text-indent"));
    if (el.normalName().equals("li") && !liMarkers.isEmpty()) {
      final int marker = liMarkers.peek();
      left += marker;
      firstLine -= marker; // first line starts at the marker; continuations hang in by marker width
    }
    return (left == 0 && right == 0 && firstLine == 0) ? BlockLayout.NONE : new BlockLayout(left, right, firstLine);
  }

  /**
   * A non-negative column indent from a CSS property (a {@code ch} value or a
   * plain number; other units are ignored).
   */
  private static int indentColumns(Element el, String property) {
    return Math.max(0, signedColumns(boxSide(el, property)));
  }

  /**
   * Parses a {@code ch} value or a plain number to whole columns; anything else
   * is 0.
   */
  private static int signedColumns(Optional<String> value) {
    if (value.isEmpty()) {
      return 0;
    }
    String v = value.get().strip().toLowerCase();
    if (v.endsWith("ch")) {
      v = v.substring(0, v.length() - 2).strip();
    }
    try {
      return Math.round(Float.parseFloat(v));
    } catch (NumberFormatException ignored) {
      return 0;
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
    flushParagraph(BlockLayout.NONE);
  }

  private void flushParagraph(BlockLayout layout) {
    if (pendingRight != null) {
      flushLeaderLine(); // a float: right span captured a right group -> leader line
      return;
    }

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
      // Borders come from the enclosing box frame (see closeBox), so the paragraph
      // itself is borderless here; indentation is its own. A run's invert comes
      // only from a dark CSS background (ComputedStyle), so an inverted lead run
      // means the whole line is a reverse-video section header: fill it to width.
      final boolean filled = runs.get(0).style().invert();
      blocks.add(new Paragraph(List.copyOf(runs), runs.get(0).style().alignment(), Border.NONE, layout, filled));
    }
    runs.clear();
  }

  /**
   * Restores the parked left group; the closed span's runs become the pending
   * right group.
   */
  private void closeFloat() {
    pendingRight = runs;
    runs = leftRuns;
    leftRuns = null;
    floatOpener = null;
    pendingSpace = false;
  }

  /**
   * Flushes the captured {@code float: right} group into a {@link LeaderLine}:
   * the
   * left group keeps its trailing space (the leader gap), the right group is
   * trimmed. An empty right group degrades to a normal paragraph.
   */
  private void flushLeaderLine() {
    pendingSpace = false;
    final List<TextRun> left = List.copyOf(runs);
    final List<TextRun> right = stripEnds(pendingRight);
    final char fill = leaderFill;
    runs.clear();
    pendingRight = null;
    leaderFill = ' ';

    if (right.isEmpty()) {
      if (!left.isEmpty()) {
        blocks.add(new Paragraph(left, left.get(0).style().alignment()));
      }
    } else {
      blocks.add(new LeaderLine(left, right, fill));
    }
  }

  /**
   * Trims leading space from the first run and trailing space from the last,
   * dropping any run left empty.
   */
  private static List<TextRun> stripEnds(List<TextRun> runs) {
    final List<TextRun> out = new ArrayList<>(runs);
    while (!out.isEmpty()) {
      final String trimmed = out.get(0).text().stripLeading();
      if (trimmed.isEmpty()) {
        out.remove(0);
      } else {
        out.set(0, new TextRun(trimmed, out.get(0).style()));
        break;
      }
    }
    while (!out.isEmpty()) {
      final int lastIndex = out.size() - 1;
      final String trimmed = out.get(lastIndex).text().stripTrailing();
      if (trimmed.isEmpty()) {
        out.remove(lastIndex);
      } else {
        out.set(lastIndex, new TextRun(trimmed, out.get(lastIndex).style()));
        break;
      }
    }
    return out;
  }

  private static boolean isBarcode(Element el) {
    return el.normalName().equals(BARCODE_TAG) || el.hasClass(BARCODE_TAG);
  }

  /**
   * Builds an {@link Image} from an {@code <img>} element: its resolved
   * {@code src} (a URL or a {@code data:} URI) and optional {@code width}/
   * {@code height} attributes. This keeps jsoup out of {@link Image}.
   */
  private static Image imageFrom(Element el) {
    if (el.attr("src").trim().isEmpty()) {
      throw new PrintingException("Element [" + el + "] must be a <img /> tag, and have a valid `src` attribute");
    }
    final Integer height = HtmlUtil.parseAttribute(el, "height").orElse(null);
    final Integer width = HtmlUtil.parseAttribute(el, "width").orElse(null);
    return Image.fromSrc(el.absUrl("src"), height, width);
  }

  /** The drawer-kick connector pin: {@code pin="5"} selects pin 5, else pin 2. */
  private static int drawerPin(Element el) {
    return el.attr("pin").strip().equals("5") ? 5 : 2;
  }

  /**
   * Cut mode: {@code mode="full"} makes a full cut, anything else a partial cut.
   */
  private static boolean cutIsPartial(Element el) {
    return !el.attr("mode").strip().equalsIgnoreCase("full");
  }

  /**
   * Feed length: the {@code lines} attribute clamped to {@code [1, 255]}, default
   * 1.
   */
  private static int feedLines(Element el) {
    return clamp(intAttr(el, "lines", 1), 1, 255);
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
    final Optional<String> position = HtmlUtil.findStyleValue(el, "position");
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
    final Optional<String> raw = HtmlUtil.findStyleValue(el, property);
    return raw.isPresent() ? Utils.lengthToDots(raw.get(), dpi, referenceDots) : OptionalInt.empty();
  }

  /**
   * Translates a sized, positioned container into a {@link Canvas}: each
   * {@code position: absolute} child is placed at its {@code left}/{@code top}
   * (dots from the top-left, {@code %} relative to the area, missing → 0). A
   * {@code transform: rotate(…)} on the container sets the whole canvas
   * direction, and one on a child rotates just that placement. Non-positioned
   * children are ignored in this version.
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
      // In page mode `transform: rotate(…)` is the region/placement rotation
      // (ESC T below), not the flow-text upside-down effect (ESC {) that
      // StyleResolver also reads it as - so clear that here (including any inherited
      // from a rotated container) to avoid double-flipping a 180° placement.
      final ComputedStyle childStyle = StyleResolver.resolve(base, child).withUpsideDown(false);
      final int x = Math.max(0, styleLength(child, "left", widthDots).orElse(0));
      final int y = Math.max(0, styleLength(child, "top", heightDots).orElse(0));
      // A `transform: rotate(…)` on the child rotates just that placement (a
      // caption down the side); no transform inherits the canvas direction (null).
      final Canvas.Direction rotation = rotationOf(child).orElse(null);
      placeableOf(child, childStyle).ifPresent(content -> placements.add(new Placement(x, y, content, rotation)));
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
    return rotationOf(container).orElse(Canvas.Direction.NORMAL);
  }

  /**
   * The {@link Canvas.Direction} for an element's {@code transform: rotate(…)},
   * or empty when it has no rotation. Used both for a container (the canvas
   * direction) and for an absolutely-positioned child (a per-placement rotation).
   */
  private static Optional<Canvas.Direction> rotationOf(Element el) {
    final Optional<String> transform = HtmlUtil.findStyleValue(el, "transform");
    if (transform.isEmpty()) {
      return Optional.empty();
    }
    final Matcher matcher = ROTATE.matcher(transform.get());
    if (!matcher.find()) {
      return Optional.empty();
    }
    // Snap to the nearest right angle and normalize to [0, 360). CSS rotation is
    // clockwise, so 90deg = a clockwise quarter turn.
    final int degrees = ((int) Math.round(Double.parseDouble(matcher.group(1)) / 90.0) * 90 % 360 + 360) % 360;
    return Optional.of(switch (degrees) {
      case 90 -> Canvas.Direction.ROTATE_90_CW;
      case 180 -> Canvas.Direction.ROTATE_180;
      case 270 -> Canvas.Direction.ROTATE_90_CCW;
      default -> Canvas.Direction.NORMAL;
    });
  }

  private static boolean isAbsolutelyPositioned(Element el) {
    return HtmlUtil.findStyleValue(el, "position")
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
      return Optional.of(new ImageBlock(imageFrom(el), style.alignment()));
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
        final ComputedStyle cellStyle = tag.equals("th") ? asHeader(StyleResolver.resolve(base, cell))
            : StyleResolver.resolve(base, cell);
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
    final Optional<String> css = HtmlUtil.findStyleValue(table, "border").map(v -> v.toLowerCase());
    final Optional<String> cssStyle = HtmlUtil.findStyleValue(table, "border-style").map(v -> v.toLowerCase());

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
          collectInline(el, StyleResolver.resolve(style, el), runs, pendingSpace);
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
