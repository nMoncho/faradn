package net.nmoncho.faradn.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
}
