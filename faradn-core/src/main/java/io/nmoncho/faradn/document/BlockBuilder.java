package io.nmoncho.faradn.document;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import io.nmoncho.faradn.Image;

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

  private BlockBuilder() {
    styles.push(ComputedStyle.INITIAL);
  }

  /**
   * Builds the block sequence for a document.
   *
   * @param doc
   *        parsed document to translate
   * @return immutable list of blocks, in reading order
   */
  public static List<Block> build(org.jsoup.nodes.Document doc) {
    final BlockBuilder builder = new BlockBuilder();
    doc.body().traverse(builder);
    builder.flushParagraph();

    return List.copyOf(builder.blocks);
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

  private static Optional<Table> buildTable(Element table, ComputedStyle base) {
    final List<List<Cell>> rows = new ArrayList<>();
    for (Element tr : table.select("tr")) {
      final List<Cell> cells = new ArrayList<>();
      for (Element cell : tr.children()) {
        final String tag = cell.normalName();
        if (!tag.equals("td") && !tag.equals("th")) {
          continue;
        }
        ComputedStyle style = base.process(cell);
        if (tag.equals("th")) {
          style = new ComputedStyle(true, style.underline(), style.widthMultiple(), style.heightMultiple(),
              style.alignment(), style.invert());
        }
        final String text = cell.text().replaceAll("\\s+", " ").strip();
        final List<TextRun> content = text.isEmpty() ? List.of() : List.of(new TextRun(text, style));
        cells.add(new Cell(content, style.alignment()));
      }
      if (!cells.isEmpty()) {
        rows.add(cells);
      }
    }
    return rows.isEmpty() ? Optional.empty() : Optional.of(new Table(rows));
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
