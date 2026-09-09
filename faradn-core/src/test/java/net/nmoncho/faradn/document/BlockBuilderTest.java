package net.nmoncho.faradn.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Document;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;

public class BlockBuilderTest {

  @Test
  void simpleParagraphWithBoldRun() {
    final List<Block> blocks = Document.from("<p>a <b>b</b> c</p>").blocks();

    assertEquals(1, blocks.size());
    final Paragraph p = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(Alignment.LEFT, p.alignment());

    assertEquals(3, p.runs().size());
    assertEquals("a ", p.runs().get(0).text());
    assertFalse(p.runs().get(0).style().bold());
    assertEquals("b ", p.runs().get(1).text());
    assertTrue(p.runs().get(1).style().bold());
    assertEquals("c", p.runs().get(2).text());
    assertFalse(p.runs().get(2).style().bold());
  }

  @Test
  void consecutiveRunsWithSameStyleMerge() {
    final List<Block> blocks = Document.from("<p><b>a</b><b>c</b></p>").blocks();

    final Paragraph p = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(1, p.runs().size());
    assertEquals("ac", p.runs().get(0).text());
    assertTrue(p.runs().get(0).style().bold());
  }

  @Test
  void whitespaceCollapses() {
    final List<Block> blocks = Document.from("<p>  a\n\n   b\t c  </p>").blocks();

    final Paragraph p = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(1, p.runs().size());
    assertEquals("a b c", p.runs().get(0).text());
  }

  @Test
  void blockTagsSeparateParagraphs() {
    final List<Block> blocks = Document.from("<div>one</div><div>two</div>").blocks();

    assertEquals(2, blocks.size());
    assertEquals("one", assertInstanceOf(Paragraph.class, blocks.get(0)).runs().get(0).text());
    assertEquals("two", assertInstanceOf(Paragraph.class, blocks.get(1)).runs().get(0).text());
  }

  @Test
  void lineBreaksSplitParagraphs() {
    final List<Block> blocks = Document.from("<p>one<br>two</p>").blocks();

    assertEquals(2, blocks.size());
    assertEquals("one", assertInstanceOf(Paragraph.class, blocks.get(0)).runs().get(0).text());
    assertEquals("two", assertInstanceOf(Paragraph.class, blocks.get(1)).runs().get(0).text());
  }

  @Test
  void headingBecomesDoubleSizedBoldParagraph() {
    final List<Block> blocks = Document.from("<h1>Title</h1>").blocks();

    final Paragraph p = assertInstanceOf(Paragraph.class, blocks.get(0));
    final ComputedStyle style = p.runs().get(0).style();
    assertTrue(style.bold());
    assertEquals(2, style.widthMultiple());
    assertEquals(2, style.heightMultiple());
  }

  @Test
  void horizontalRuleBecomesRule() {
    final List<Block> blocks = Document.from("<p>a</p><hr><p>b</p>").blocks();

    assertEquals(3, blocks.size());
    assertInstanceOf(Rule.class, blocks.get(1));
  }

  @Test
  void alignmentInheritsFromEnclosingDiv() {
    final List<Block> blocks = Document
        .from("<div style=\"text-align: right\"><p>a</p><p><b>b</b></p></div>")
        .blocks();

    assertEquals(2, blocks.size());
    assertEquals(Alignment.RIGHT, assertInstanceOf(Paragraph.class, blocks.get(0)).alignment());
    assertEquals(Alignment.RIGHT, assertInstanceOf(Paragraph.class, blocks.get(1)).alignment());
  }

  @Test
  void styleDoesNotLeakAfterElementCloses() {
    final List<Block> blocks = Document.from("<p><b>bold</b></p><p>plain</p>").blocks();

    assertTrue(assertInstanceOf(Paragraph.class, blocks.get(0)).runs().get(0).style().bold());
    assertFalse(assertInstanceOf(Paragraph.class, blocks.get(1)).runs().get(0).style().bold());
  }

  @Test
  void paragraphResource() {
    final List<Block> blocks = Document.from(new File("src/test/resources/elementjobs/paragraph.html")).blocks();

    assertEquals(5, blocks.size());

    // <em>/<i> mark italic, splitting the first paragraph into alternating runs
    final Paragraph first = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(List.of(false, true, false, true, false),
        first.runs().stream().map(run -> run.style().italic()).toList());
    assertEquals("sit", first.runs().get(1).text().strip());
    assertEquals("elit", first.runs().get(3).text().strip());
    assertFalse(first.runs().get(0).style().bold());

    // <b>/<strong> split the second paragraph into alternating runs
    final Paragraph second = assertInstanceOf(Paragraph.class, blocks.get(1));
    assertEquals(List.of(false, true, false, true, false),
        second.runs().stream().map(run -> run.style().bold()).toList());
    assertEquals("Nulla", second.runs().get(1).text().strip());
    assertEquals("Duis", second.runs().get(3).text().strip());

    // Inline CSS on <span>: font-style italic and font-weight bold each split the run
    final Paragraph fifth = assertInstanceOf(Paragraph.class, blocks.get(4));
    assertEquals(5, fifth.runs().size());
    assertEquals("non", fifth.runs().get(1).text().strip());
    assertTrue(fifth.runs().get(1).style().italic());
    assertFalse(fifth.runs().get(1).style().bold());
    assertEquals("hendrerit", fifth.runs().get(3).text().strip());
    assertTrue(fifth.runs().get(3).style().bold());
    assertFalse(fifth.runs().get(3).style().italic());
  }

  @Test
  void ticketResource() {
    final List<Block> blocks = Document.from(new File("src/test/resources/printjobs/ticket01.html")).blocks();

    assertEquals(4, blocks.size());

    final ImageBlock image = assertInstanceOf(ImageBlock.class, blocks.get(0));
    assertEquals(Alignment.CENTER, image.alignment());

    for (int i = 1; i < 4; i++) {
      final Paragraph p = assertInstanceOf(Paragraph.class, blocks.get(i));
      assertEquals(Alignment.CENTER, p.alignment());
      assertTrue(p.runs().get(0).style().bold());
    }
    assertEquals("Cannibale Royale", assertInstanceOf(Paragraph.class, blocks.get(1)).runs().get(0).text());
  }

  @Test
  void barcodeResource() {
    final List<Block> blocks = Document.from(new File("src/test/resources/elementjobs/barcode.html")).blocks();

    assertEquals(1, blocks.size());
    final Barcode barcode = assertInstanceOf(Barcode.class, blocks.get(0));
    assertEquals("72527273073", barcode.data());
    assertEquals("upc-a", barcode.symbology());
  }

  @Test
  void barcodeCustomElement() {
    final List<Block> blocks = Document.from("<bar-code>12345678</bar-code>").blocks();

    final Barcode barcode = assertInstanceOf(Barcode.class, blocks.get(0));
    assertEquals("12345678", barcode.data());
    assertEquals(Barcode.DEFAULT_SYMBOLOGY, barcode.symbology());
  }

  @Test
  void barcodeDefaultsWhenNoOptionAttributes() {
    final List<Block> blocks = Document.from("<bar-code>12345678</bar-code>").blocks();

    final Barcode barcode = assertInstanceOf(Barcode.class, blocks.get(0));
    assertEquals(BarcodeOptions.DEFAULT, barcode.options());
  }

  @Test
  void barcodeParsesOptionAttributes() {
    final List<Block> blocks = Document
        .from("<bar-code symbology=\"qr\" height=\"60\" module=\"8\" hri=\"none\" ec=\"h\">hi</bar-code>")
        .blocks();

    final Barcode barcode = assertInstanceOf(Barcode.class, blocks.get(0));
    assertEquals(new BarcodeOptions(60, 8, BarcodeOptions.Hri.NONE, BarcodeOptions.QrEc.H), barcode.options());
  }

  @Test
  void barcodeClampsOutOfRangeOptionAttributes() {
    final List<Block> blocks = Document.from("<bar-code height=\"9000\" module=\"-5\">12345678</bar-code>").blocks();

    final Barcode barcode = assertInstanceOf(Barcode.class, blocks.get(0));
    assertEquals(255, barcode.options().heightDots());
    assertEquals(0, barcode.options().moduleSize());
  }

  @Test
  void barcodeTextIsNotAlsoAParagraph() {
    final List<Block> blocks = Document.from("<bar-code>12345678</bar-code><p>after</p>").blocks();

    assertEquals(2, blocks.size());
    assertInstanceOf(Barcode.class, blocks.get(0));
    assertEquals("after", assertInstanceOf(Paragraph.class, blocks.get(1)).runs().get(0).text());
  }

  @Test
  void tableCellKeepsInlineStyledRuns() {
    final List<Block> blocks = Document.from("<table><tr><td>a <b>b</b> c</td></tr></table>").blocks();

    final Table table = assertInstanceOf(Table.class, blocks.get(0));
    final Cell cell = table.rows().get(0).get(0);
    // A collapsed space between runs attaches to the preceding run (as for paragraphs).
    assertEquals(3, cell.content().size());
    assertEquals("a ", cell.content().get(0).text());
    assertFalse(cell.content().get(0).style().bold());
    assertEquals("b ", cell.content().get(1).text());
    assertTrue(cell.content().get(1).style().bold());
    assertEquals("c", cell.content().get(2).text());
    assertFalse(cell.content().get(2).style().bold());
  }

  @Test
  void tableHeaderCellRunsAreBold() {
    final List<Block> blocks = Document.from("<table><tr><th>Qty</th></tr></table>").blocks();

    final Cell cell = assertInstanceOf(Table.class, blocks.get(0)).rows().get(0).get(0);
    assertEquals(1, cell.content().size());
    assertTrue(cell.content().get(0).style().bold());
  }

  @Test
  void tableCellParsesColspan() {
    final List<Block> blocks = Document
        .from("<table><tr><td colspan=\"3\">wide</td></tr></table>").blocks();

    final Cell cell = assertInstanceOf(Table.class, blocks.get(0)).rows().get(0).get(0);
    assertEquals(3, cell.colSpan());
  }

  @Test
  void tableCellDefaultsToSingleColumnSpan() {
    final List<Block> blocks = Document.from("<table><tr><td>x</td></tr></table>").blocks();

    final Cell cell = assertInstanceOf(Table.class, blocks.get(0)).rows().get(0).get(0);
    assertEquals(1, cell.colSpan());
  }

  @Test
  void spanAppliesInlineStyleToASection() {
    final List<Block> blocks = Document.from("<p>a<span style=\"font-weight: bold\">b</span>c</p>").blocks();

    final Paragraph paragraph = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(3, paragraph.runs().size());
    assertEquals("a", paragraph.runs().get(0).text());
    assertFalse(paragraph.runs().get(0).style().bold());
    assertEquals("b", paragraph.runs().get(1).text());
    assertTrue(paragraph.runs().get(1).style().bold());
    assertEquals("c", paragraph.runs().get(2).text());
    assertFalse(paragraph.runs().get(2).style().bold());
  }

  @Test
  void spanCanOverrideAnInheritedStyle() {
    // A <span> can switch a style off again, e.g. un-bold a section inside a heading.
    final List<Block> blocks = Document
        .from("<h3>Title <span style=\"font-weight: normal\">sub</span></h3>").blocks();

    final Paragraph paragraph = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(2, paragraph.runs().size());
    assertEquals("Title ", paragraph.runs().get(0).text());
    assertTrue(paragraph.runs().get(0).style().bold());
    assertEquals("sub", paragraph.runs().get(1).text());
    assertFalse(paragraph.runs().get(1).style().bold());
  }

  @Test
  void spanInsideACellStylesPartOfItsContent() {
    final List<Block> blocks = Document
        .from("<table><tr><td>x<span style=\"text-decoration: underline\">y</span></td></tr></table>").blocks();

    final Cell cell = assertInstanceOf(Table.class, blocks.get(0)).rows().get(0).get(0);
    assertEquals(2, cell.content().size());
    assertEquals("x", cell.content().get(0).text());
    assertFalse(cell.content().get(0).style().underline());
    assertEquals("y", cell.content().get(1).text());
    assertTrue(cell.content().get(1).style().underline());
  }

  @Test
  void emptyDocumentYieldsNoBlocks() {
    assertTrue(Document.from("<div>   \n  </div>").blocks().isEmpty());
  }

  // ----- page mode: position:relative container -> Canvas -----

  private static final String IMG_SRC = "data:image/png;base64,AAAA";

  @Test
  void positionedContainerBecomesCanvas() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 512px; height: 160px\">"
            + "<span style=\"position: absolute; left: 0; top: 0\">Order #42</span>"
            + "<span style=\"position: absolute; left: 320px; top: 40px\">Table 7</span>"
            + "</div>")
        .blocks();

    assertEquals(1, blocks.size());
    final Canvas canvas = assertInstanceOf(Canvas.class, blocks.get(0));
    assertEquals(512, canvas.widthDots());
    assertEquals(160, canvas.heightDots());
    assertEquals(Canvas.Direction.NORMAL, canvas.direction());
    assertEquals(2, canvas.placements().size());

    final Placement first = canvas.placements().get(0);
    assertEquals(0, first.xDots());
    assertEquals(0, first.yDots());
    assertEquals("Order #42", assertInstanceOf(Paragraph.class, first.content()).runs().get(0).text());

    final Placement second = canvas.placements().get(1);
    assertEquals(320, second.xDots());
    assertEquals(40, second.yDots());
    assertEquals("Table 7", assertInstanceOf(Paragraph.class, second.content()).runs().get(0).text());
  }

  @Test
  void absolutePositionedContainerAlsoBecomesCanvas() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: absolute; width: 200px; height: 80px\">"
            + "<span style=\"position: absolute; left: 5px; top: 5px\">x</span></div>")
        .blocks();

    assertInstanceOf(Canvas.class, blocks.get(0));
  }

  @Test
  void canvasResolvesMmAndCmWithDpi() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 80mm; height: 40mm\">"
            + "<span style=\"position: absolute; left: 1cm; top: 2cm\">x</span></div>")
        .blocks(180);

    final Canvas canvas = assertInstanceOf(Canvas.class, blocks.get(0));
    assertEquals(567, canvas.widthDots()); // 80 * 180 / 25.4
    assertEquals(283, canvas.heightDots()); // 40 * 180 / 25.4
    final Placement p = canvas.placements().get(0);
    assertEquals(71, p.xDots()); // 10mm -> 10 * 180 / 25.4
    assertEquals(142, p.yDots()); // 20mm -> 20 * 180 / 25.4
  }

  @Test
  void canvasResolvesPercentAgainstArea() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 400px; height: 200px\">"
            + "<span style=\"position: absolute; left: 50%; top: 25%\">x</span></div>")
        .blocks();

    final Placement p = assertInstanceOf(Canvas.class, blocks.get(0)).placements().get(0);
    assertEquals(200, p.xDots()); // 50% of 400
    assertEquals(50, p.yDots()); // 25% of 200
  }

  @Test
  void canvasChildTypesMapToPlaceables() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 512px; height: 200px\">"
            + "<img style=\"position: absolute; left: 0; top: 0\" width=\"8\" height=\"8\" src=\"" + IMG_SRC + "\">"
            + "<bar-code style=\"position: absolute; left: 0; top: 100px\" symbology=\"code128\">FARADN</bar-code>"
            + "<span style=\"position: absolute; left: 0; top: 150px\">hi</span>"
            + "</div>")
        .blocks();

    final Canvas canvas = assertInstanceOf(Canvas.class, blocks.get(0));
    assertEquals(3, canvas.placements().size());
    assertInstanceOf(ImageBlock.class, canvas.placements().get(0).content());
    final Barcode barcode = assertInstanceOf(Barcode.class, canvas.placements().get(1).content());
    assertEquals("FARADN", barcode.data());
    assertEquals("code128", barcode.symbology());
    assertInstanceOf(Paragraph.class, canvas.placements().get(2).content());
  }

  @Test
  void canvasChildMissingLeftTopDefaultsToZero() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 300px; height: 100px\">"
            + "<span style=\"position: absolute\">x</span></div>")
        .blocks();

    final Placement p = assertInstanceOf(Canvas.class, blocks.get(0)).placements().get(0);
    assertEquals(0, p.xDots());
    assertEquals(0, p.yDots());
  }

  @Test
  void canvasIgnoresNonAbsolutelyPositionedChildren() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 300px; height: 100px\">"
            + "<span style=\"position: absolute; left: 10px; top: 10px\">keep</span>"
            + "<span>drop</span>"
            + "<span style=\"position: relative\">also drop</span>"
            + "</div>")
        .blocks();

    final Canvas canvas = assertInstanceOf(Canvas.class, blocks.get(0));
    assertEquals(1, canvas.placements().size());
    assertEquals("keep",
        assertInstanceOf(Paragraph.class, canvas.placements().get(0).content()).runs().get(0).text());
  }

  @Test
  void canvasClampsNegativePositionsToZero() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 300px; height: 100px\">"
            + "<span style=\"position: absolute; left: -20px; top: -5px\">x</span></div>")
        .blocks();

    final Placement p = assertInstanceOf(Canvas.class, blocks.get(0)).placements().get(0);
    assertEquals(0, p.xDots());
    assertEquals(0, p.yDots());
  }

  @Test
  void relativeContainerWithoutSizeIsNotCanvas() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative\"><span>hi</span></div>").blocks();

    assertTrue(blocks.stream().noneMatch(b -> b instanceof Canvas));
    assertEquals("hi", assertInstanceOf(Paragraph.class, blocks.get(0)).runs().get(0).text());
  }

  private static Canvas.Direction canvasDirection(String extraStyle) {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 300px; height: 100px" + extraStyle + "\">"
            + "<span style=\"position: absolute; left: 0; top: 0\">x</span></div>")
        .blocks();
    return assertInstanceOf(Canvas.class, blocks.get(0)).direction();
  }

  @Test
  void canvasRotationFromCssTransform() {
    assertEquals(Canvas.Direction.NORMAL, canvasDirection(""));
    assertEquals(Canvas.Direction.ROTATE_90_CW, canvasDirection("; transform: rotate(90deg)"));
    assertEquals(Canvas.Direction.ROTATE_180, canvasDirection("; transform: rotate(180deg)"));
    assertEquals(Canvas.Direction.ROTATE_90_CCW, canvasDirection("; transform: rotate(270deg)"));
  }

  @Test
  void canvasRotationNormalizesNegativeAndFullTurns() {
    assertEquals(Canvas.Direction.ROTATE_90_CCW, canvasDirection("; transform: rotate(-90deg)"));
    assertEquals(Canvas.Direction.ROTATE_90_CW, canvasDirection("; transform: rotate(-270deg)"));
    assertEquals(Canvas.Direction.NORMAL, canvasDirection("; transform: rotate(0deg)"));
    assertEquals(Canvas.Direction.NORMAL, canvasDirection("; transform: rotate(360deg)"));
  }

  @Test
  void canvasRotationSnapsToNearestRightAngleAndIgnoresOther() {
    assertEquals(Canvas.Direction.ROTATE_90_CW, canvasDirection("; transform: rotate(88deg)")); // -> 90
    assertEquals(Canvas.Direction.NORMAL, canvasDirection("; transform: rotate(30deg)")); // -> 0
    assertEquals(Canvas.Direction.NORMAL, canvasDirection("; transform: scale(2)")); // no rotate()
    assertEquals(Canvas.Direction.ROTATE_90_CW,
        canvasDirection("; transform: translate(4px, 4px) rotate(90deg)")); // combined
  }

  @Test
  void placementTransformSetsPerPlacementRotation() {
    final List<Block> blocks = Document.from(
        "<div style=\"position: relative; width: 512px; height: 160px\">"
            + "<span style=\"position: absolute; left: 0; top: 0\">flat</span>"
            + "<span style=\"position: absolute; left: 480px; top: 8px; transform: rotate(90deg)\">VOID</span>"
            + "</div>")
        .blocks();

    final Canvas canvas = assertInstanceOf(Canvas.class, blocks.get(0));
    assertEquals(Canvas.Direction.NORMAL, canvas.direction());
    // A child without a transform inherits the canvas (null); one with a transform rotates on its own.
    assertNull(canvas.placements().get(0).rotation());
    assertEquals(Canvas.Direction.ROTATE_90_CW, canvas.placements().get(1).rotation());
  }

  // ----- whole-job labels: a sized <body> becomes one Canvas -----

  @Test
  void sizedBodyBecomesWholeJobCanvas() {
    final List<Block> blocks = Document.from(
        "<body style=\"width: 400px; height: 600px\">"
            + "<span style=\"position: absolute; left: 10px; top: 20px\">Name</span>"
            + "<bar-code style=\"position: absolute; left: 10px; top: 300px\" symbology=\"qr\">ID-42</bar-code>"
            + "</body>")
        .blocks();

    assertEquals(1, blocks.size());
    final Canvas canvas = assertInstanceOf(Canvas.class, blocks.get(0));
    assertEquals(400, canvas.widthDots());
    assertEquals(600, canvas.heightDots());
    assertEquals(Canvas.Direction.NORMAL, canvas.direction());
    assertEquals(2, canvas.placements().size());
    assertEquals(10, canvas.placements().get(0).xDots());
    assertEquals(20, canvas.placements().get(0).yDots());
    assertEquals("Name",
        assertInstanceOf(Paragraph.class, canvas.placements().get(0).content()).runs().get(0).text());
    assertInstanceOf(Barcode.class, canvas.placements().get(1).content());
  }

  @Test
  void sizedBodyRotates() {
    final List<Block> blocks = Document.from(
        "<body style=\"width: 400px; height: 600px; transform: rotate(180deg)\">"
            + "<span style=\"position: absolute; left: 0; top: 0\">x</span></body>")
        .blocks();

    assertEquals(Canvas.Direction.ROTATE_180, assertInstanceOf(Canvas.class, blocks.get(0)).direction());
  }

  @Test
  void sizedBodyIgnoresNonPositionedContent() {
    final List<Block> blocks = Document.from(
        "<body style=\"width: 300px; height: 200px\"><p>flow</p>"
            + "<span style=\"position: absolute; left: 5px; top: 5px\">placed</span></body>")
        .blocks();

    final Canvas canvas = assertInstanceOf(Canvas.class, blocks.get(0));
    assertEquals(1, canvas.placements().size());
    assertEquals("placed",
        assertInstanceOf(Paragraph.class, canvas.placements().get(0).content()).runs().get(0).text());
  }

  @Test
  void unsizedBodyStaysStandardFlow() {
    final List<Block> blocks = Document.from("<body><p>hi</p></body>").blocks();

    assertTrue(blocks.stream().noneMatch(b -> b instanceof Canvas));
    assertEquals("hi", assertInstanceOf(Paragraph.class, blocks.get(0)).runs().get(0).text());
  }

  @Test
  void lineHeightIsResolvedOnRuns() {
    final List<Block> blocks = Document.from("<p style=\"line-height: 1.5\">hi</p>").blocks();

    final Paragraph p = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(new LineHeight(LineHeight.Kind.FACTOR, 1.5), p.runs().get(0).style().lineHeight());
  }

  @Test
  void lineHeightInheritsFromAncestor() {
    final List<Block> blocks = Document.from("<div style=\"line-height: 2\"><p>hi</p></div>").blocks();

    final Paragraph p = assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(new LineHeight(LineHeight.Kind.FACTOR, 2), p.runs().get(0).style().lineHeight());
  }

  // ----- table borders -----

  private static Table table(String html) {
    return assertInstanceOf(Table.class, Document.from(html).blocks().get(0));
  }

  @Test
  void tableBorderAttributeMakesSingleGrid() {
    final Table t = table("<table border=\"1\"><tr><td>a</td></tr></table>");

    assertEquals(Border.all(Border.Style.SINGLE), t.outer());
    assertTrue(t.gridLines());
    assertTrue(t.bordered());
  }

  @Test
  void tableBorderZeroOrAbsentHasNoBorder() {
    assertEquals(Border.NONE, table("<table><tr><td>a</td></tr></table>").outer());
    assertEquals(Border.NONE, table("<table border=\"0\"><tr><td>a</td></tr></table>").outer());
    assertFalse(table("<table><tr><td>a</td></tr></table>").bordered());
  }

  @Test
  void tableCssBorderStyleDoubleMakesDoubleGrid() {
    assertEquals(Border.all(Border.Style.DOUBLE),
        table("<table style=\"border-style: double\"><tr><td>a</td></tr></table>").outer());
    assertEquals(Border.all(Border.Style.DOUBLE),
        table("<table style=\"border: 2px double\"><tr><td>a</td></tr></table>").outer());
  }

  @Test
  void tableCssBorderNoneOverridesAttribute() {
    assertEquals(Border.NONE,
        table("<table border=\"1\" style=\"border: none\"><tr><td>a</td></tr></table>").outer());
  }

  // ----- paragraph borders (top / bottom rules) -----

  private static Paragraph paragraph(String html) {
    return assertInstanceOf(Paragraph.class, Document.from(html).blocks().get(0));
  }

  @Test
  void paragraphBorderBottomParsed() {
    final Paragraph p = paragraph("<p style=\"border-bottom: 1px solid\">x</p>");

    assertTrue(p.border().bottom());
    assertFalse(p.border().top());
    assertEquals(Border.Style.SINGLE, p.border().style());
  }

  @Test
  void paragraphBorderShorthandSetsAllSidesAndDouble() {
    final Paragraph p = paragraph("<div style=\"border: 2px double\">x</div>");

    assertTrue(p.border().top());
    assertTrue(p.border().bottom());
    assertEquals(Border.Style.DOUBLE, p.border().style());
  }

  @Test
  void paragraphHasNoBorderByDefaultOrWhenNone() {
    assertEquals(Border.NONE, paragraph("<p>x</p>").border());
    assertEquals(Border.NONE, paragraph("<p style=\"border: none\">x</p>").border());
  }

  // ----- boxes: a bordered container wrapping several blocks -----

  @Test
  void borderedDivWithMultipleBlocksBecomesBox() {
    final List<Block> blocks = Document.from(
        "<div style=\"border: 1px solid\"><p>a</p><p>b</p></div>").blocks();

    assertEquals(1, blocks.size());
    final Box box = assertInstanceOf(Box.class, blocks.get(0));
    assertEquals(Border.all(Border.Style.SINGLE), box.border());
    assertEquals(2, box.children().size());
    assertEquals("a", assertInstanceOf(Paragraph.class, box.children().get(0)).runs().get(0).text());
    assertEquals("b", assertInstanceOf(Paragraph.class, box.children().get(1)).runs().get(0).text());
  }

  @Test
  void borderedDivWithSingleParagraphStaysABorderedParagraph() {
    final Paragraph p = paragraph("<div style=\"border: 1px solid\">solo</div>");

    assertEquals(Border.all(Border.Style.SINGLE), p.border());
  }

  @Test
  void borderedDivKeepsChildBlockTypesInsideTheBox() {
    final Box box = assertInstanceOf(Box.class, Document.from(
        "<div style=\"border: 1px solid\"><p>a</p><hr><p>b</p></div>").blocks().get(0));

    assertEquals(3, box.children().size());
    assertInstanceOf(Paragraph.class, box.children().get(0));
    assertInstanceOf(Rule.class, box.children().get(1));
    assertInstanceOf(Paragraph.class, box.children().get(2));
  }

  // ----- leader / space-between lines (float: right) -----

  @Test
  void floatRightSpanBecomesLeaderLine() {
    final List<Block> blocks = Document.from(
        "<p>Subtotal <span style=\"float: right\">9,00</span></p>").blocks();

    assertEquals(1, blocks.size());
    final LeaderLine leader = assertInstanceOf(LeaderLine.class, blocks.get(0));
    assertEquals("Subtotal ", leader.left().get(0).text()); // trailing space kept for the gap
    assertEquals("9,00", leader.right().get(0).text());
    assertEquals(' ', leader.fill());
  }

  @Test
  void dataLeaderSetsTheFillCharacter() {
    final LeaderLine leader = assertInstanceOf(LeaderLine.class, Document.from(
        "<p>Total <span style=\"float: right\" data-leader=\".\">12,50</span></p>").blocks().get(0));

    assertEquals('.', leader.fill());
  }

  @Test
  void floatRightGroupsCarryRunStyles() {
    final LeaderLine leader = assertInstanceOf(LeaderLine.class, Document.from(
        "<p><b>Total</b> <span style=\"float: right\"><b>9,00</b></span></p>").blocks().get(0));

    assertTrue(leader.left().get(0).style().bold());
    assertTrue(leader.right().get(0).style().bold());
  }

  @Test
  void noFloatIsANormalParagraph() {
    assertInstanceOf(Paragraph.class, Document.from("<p>Subtotal 9,00</p>").blocks().get(0));
  }

  // ----- block margins & indentation -----

  @Test
  void marginAndPaddingIndentAsColumns() {
    final Paragraph p = paragraph("<p style=\"margin-left: 2ch; padding-left: 1ch; margin-right: 3ch\">x</p>");

    assertEquals(3, p.layout().leftIndent()); // 2 + 1
    assertEquals(3, p.layout().rightIndent());
    assertEquals(0, p.layout().firstLineIndent());
  }

  @Test
  void textIndentSetsFirstLineIndent() {
    assertEquals(4, paragraph("<p style=\"text-indent: 4ch\">x</p>").layout().firstLineIndent());
    assertEquals(-2, paragraph("<p style=\"text-indent: -2ch\">x</p>").layout().firstLineIndent());
  }

  @Test
  void listItemHangsByItsMarkerWidth() {
    final Paragraph p = paragraph("<ol><li>x</li></ol>");

    assertEquals(3, p.layout().leftIndent()); // "1. "
    assertEquals(-3, p.layout().firstLineIndent()); // hanging
  }

  @Test
  void plainNumberIsColumnsAndUnknownUnitsIgnored() {
    assertEquals(2, paragraph("<p style=\"margin-left: 2\">x</p>").layout().leftIndent());
    assertEquals(0, paragraph("<p style=\"margin-left: 20px\">x</p>").layout().leftIndent()); // px not mapped (v1)
    assertEquals(BlockLayout.NONE, paragraph("<p>x</p>").layout());
  }

  // ----- vertical spacing (margin-top / margin-bottom) -----

  @Test
  void marginTopInsertsSpaceBeforeTheBlock() {
    final List<Block> blocks = Document.from("<p style=\"margin-top: 24px\">a</p>").blocks();

    assertEquals(2, blocks.size());
    assertEquals(24, assertInstanceOf(Space.class, blocks.get(0)).dots());
    assertInstanceOf(Paragraph.class, blocks.get(1));
  }

  @Test
  void marginBottomInsertsSpaceAfterTheBlock() {
    final List<Block> blocks = Document.from("<div style=\"margin-bottom: 12px\">a</div>").blocks();

    assertInstanceOf(Paragraph.class, blocks.get(0));
    assertEquals(12, assertInstanceOf(Space.class, blocks.get(1)).dots());
  }

  @Test
  void marginInMillimetresUsesDpi() {
    final List<Block> blocks = Document.from("<p style=\"margin-top: 5mm\">a</p>").blocks(180);

    assertEquals(35, assertInstanceOf(Space.class, blocks.get(0)).dots()); // 5 * 180 / 25.4
  }

  @Test
  void noMarginProducesNoSpace() {
    assertEquals(1, Document.from("<p>a</p>").blocks().size());
  }

  @Test
  void marginIsOutsideAndPaddingIsInsideTheBox() {
    final List<Block> blocks = Document.from(
        "<div style=\"border: 1px solid; margin-top: 24px; padding-top: 24px; padding-bottom: 24px;"
            + " margin-bottom: 24px\"><p>x</p></div>")
        .blocks();

    assertEquals(3, blocks.size());
    assertEquals(24, assertInstanceOf(Space.class, blocks.get(0)).dots()); // margin-top: outside
    final Box box = assertInstanceOf(Box.class, blocks.get(1));
    assertEquals(24, assertInstanceOf(Space.class, box.children().get(0)).dots()); // padding-top: inside
    assertInstanceOf(Paragraph.class, box.children().get(1));
    assertEquals(24, assertInstanceOf(Space.class, box.children().get(2)).dots()); // padding-bottom: inside
    assertEquals(24, assertInstanceOf(Space.class, blocks.get(2)).dots()); // margin-bottom: outside
  }

  @Test
  void marginAndPaddingShorthandsSetVerticalSpace() {
    final List<Block> blocks = Document.from(
        "<div style=\"border: 1px solid; margin: 10px; padding: 6px\"><p>x</p></div>").blocks();

    assertEquals(10, assertInstanceOf(Space.class, blocks.get(0)).dots()); // margin shorthand -> top
    final Box box = assertInstanceOf(Box.class, blocks.get(1));
    assertEquals(6, assertInstanceOf(Space.class, box.children().get(0)).dots()); // padding shorthand -> top
    assertEquals(10, assertInstanceOf(Space.class, blocks.get(2)).dots()); // margin shorthand -> bottom
  }

  @Test
  void borderlessPaddingIsJustSpaceBeforeTheBlock() {
    final List<Block> blocks = Document.from("<p style=\"padding-top: 12px\">x</p>").blocks();

    assertEquals(12, assertInstanceOf(Space.class, blocks.get(0)).dots());
    assertInstanceOf(Paragraph.class, blocks.get(1));
  }

  /**
   * For a bordered single-paragraph block, asserts the outer margin Spaces (top
   * first, bottom last).
   */
  private static void assertVerticalMargin(List<Block> blocks, int topDots, int bottomDots) {
    assertEquals(topDots, assertInstanceOf(Space.class, blocks.get(0)).dots());
    assertEquals(bottomDots, assertInstanceOf(Space.class, blocks.get(blocks.size() - 1)).dots());
  }

  @Test
  void marginShorthandExpandsOneToFourValues() {
    final String open = "<div style=\"border: 1px solid; margin: ";
    assertVerticalMargin(Document.from(open + "10px\">x</div>").blocks(), 10, 10); // 1: all sides
    assertVerticalMargin(Document.from(open + "10px 20px\">x</div>").blocks(), 10, 10); // 2: top/bottom
    assertVerticalMargin(Document.from(open + "5px 9px 15px\">x</div>").blocks(), 5, 15); // 3: top, _, bottom
    assertVerticalMargin(Document.from(open + "5px 9px 15px 9px\">x</div>").blocks(), 5, 15); // 4: t r b l
  }

  @Test
  void paddingShorthandIndentsLeftAndRight() {
    // top/bottom 1ch (no vertical dots from ch); left/right 4ch -> a 4-column indent.
    final Paragraph p = paragraph("<p style=\"padding: 1ch 4ch\">x</p>");

    assertEquals(4, p.layout().leftIndent());
    assertEquals(4, p.layout().rightIndent());
  }

  @Test
  void longhandOverridesTheShorthandSide() {
    final List<Block> blocks = Document.from(
        "<div style=\"border: 1px solid; margin: 10px; margin-top: 30px\">x</div>").blocks();

    assertVerticalMargin(blocks, 30, 10); // margin-top longhand wins; bottom from the shorthand
  }

  // ----- reverse-video section headers (background -> invert + filled) -----

  @Test
  void darkBackgroundMakesAFilledInvertedBanner() {
    final Paragraph p = paragraph("<div style=\"background: black\">TOTAL</div>");

    assertTrue(p.filled());
    assertTrue(p.runs().get(0).style().invert());
  }

  @Test
  void anyNonWhiteBackgroundInksTheLine() {
    // A monochrome printer has only black ink, so a coloured background is "black".
    assertTrue(paragraph("<div style=\"background-color: #c00\">SALE</div>").filled());
    assertTrue(paragraph("<div style=\"background: navy\">INFO</div>").filled());
  }

  @Test
  void whiteBackgroundDoesNotInkTheLine() {
    final Paragraph p = paragraph("<p style=\"background: white\">plain</p>");

    assertFalse(p.filled());
    assertFalse(p.runs().get(0).style().invert());
  }

  @Test
  void bannerInheritsToNestedBlockChildren() {
    // background is applied per line: the inner paragraph inherits the invert and
    // so becomes its own filled bar.
    final Paragraph p = paragraph("<div style=\"background: black\"><p>SECTION</p></div>");

    assertTrue(p.filled());
    assertTrue(p.runs().get(0).style().invert());
  }

  @Test
  void inlineInvertedSpanIsNotAFilledBanner() {
    // A background on an inline span inverts only that word; the paragraph as a
    // whole is not a full-width bar (its lead run is not inverted).
    final Paragraph p = paragraph("<p>normal <span style=\"background: black\">HOT</span> tail</p>");

    assertFalse(p.filled());
    assertFalse(p.runs().get(0).style().invert());
    assertTrue(p.runs().get(1).style().invert());
  }
}
