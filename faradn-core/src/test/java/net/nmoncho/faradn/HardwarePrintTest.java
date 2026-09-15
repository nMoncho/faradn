//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Cut;
import net.nmoncho.faradn.document.Feed;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.EscPosRenderer;
import net.nmoncho.faradn.transport.NetworkTransport;
import net.nmoncho.faradn.transport.UsbTransport;

/**
 * Manual hardware checks (Hardware checkpoints 1 and 2). Each talks to a real
 * printer, so each runs only when you point it at one:
 *
 * <pre>{@code
 * # over USB (Epson vendor 0x04b8)
 * mvn test -Dfaradn.hardware=true -Dtest=HardwarePrintTest
 *
 * # over Ethernet (raw TCP 9100)
 * mvn test -Dfaradn.printer.host=192.168.1.50 -Dtest=HardwarePrintTest
 * }</pre>
 *
 * The receipt tests print a full receipt exercising the logo image, a table, a
 * Code 128 barcode, a QR code and word-wrapped text (checkpoint 3), plus
 * heading
 * sizes, bold, centering, alignment, underline and rules (checkpoint 1). Verify
 * by eye that it is legible, scan the barcode and QR, and - checkpoint 2 - pull
 * the paper roll to confirm the job fails rather than hangs.
 * <p>
 * {@link #printsTableShowcaseOverUsb()} prints a separate document of several
 * table types - content-sized columns, full-width and partial {@code colspan},
 * inline-styled cells, and a four-column table - to check the character-grid
 * layout on paper: verify each table's columns line up, spanning cells cover
 * the
 * right width, and narrow columns are not padded to an even split.
 * <p>
 * {@link #printsPageModeCanvasOverUsb()} prints a fixed-size {@code Canvas}
 * (page
 * mode): text placed at exact dot positions. Verify the pieces land where their
 * {@code (x, y)} say - e.g. two labels on the same row at different x, and a
 * line
 * lower down - and that the region prints as one block.
 * <p>
 * {@link #printsPageModeHtmlOverUsb()} prints the same coupon through the full
 * HTML pipeline (a {@code position: relative} container with {@code position:
 * absolute} children), verifying the CSS-to-page-mode mapping end to end.
 * <p>
 * {@link #printsRotatedPageModeOverUsb()} prints a {@code transform:
 * rotate(180deg)} coupon (it should come out upside down), and
 * {@link #printsLabelOverUsb()} prints a whole-job label from a sized
 * {@code <body>} - a badge with a name, a role, and a scannable barcode.
 * <p>
 * {@link #printsQrPageModeOverUsb()} is a diagnostic for 2D (QR) vertical
 * anchoring in page mode: the QR should land between two marker lines and scan.
 * <p>
 * {@link #printsRotationShowcaseOverUsb()} prints four page-mode regions in one
 * job (0/90/180/-90 degrees), each a text label plus a QR, to check every print
 * direction. {@link #printsMixedModeOverUsb()} prints a single job that mixes
 * standard-mode receipt flow with an embedded page-mode coupon region, to check
 * that the two modes interleave correctly.
 * <p>
 * {@link #printsLineHeightOverUsb()} prints wrapped paragraphs at different
 * {@code line-height}s (default / 1.0 / 2.0 / 40px) so the {@code ESC 3} line
 * spacing can be compared by eye.
 * <p>
 * {@link #printsBorderedTableOverUsb()} prints tables with box-drawing grid
 * borders (single and double, plus a colspan row) to check the lines join up on
 * paper and the columns stay aligned.
 * <p>
 * {@link #printsFontSizeOverUsb()} prints lines at increasing {@code font-size}
 * (1x/2x/3x via {@code GS !}) so the magnification can be compared by eye.
 * <p>
 * {@link #printsLeaderLinesOverUsb()} prints leader / space-between lines
 * ({@code float: right}) - item prices flush right, a dotted tax line - to
 * check
 * the gap fills and the values align at the right edge.
 * <p>
 * {@link #printsIndentationOverUsb()} prints an indented note ({@code
 * margin-left}) and a list whose wrapped items hang under the text, to check
 * block margins and hanging indents.
 * <p>
 * {@link #printsSpacingOverUsb()} prints paragraphs with {@code margin-top}/
 * {@code margin-bottom} (dot feeds) to check the vertical gaps between blocks.
 * <p>
 * {@link #printsMarginPaddingOverUsb()} prints bordered boxes with margin only,
 * padding only, and both, to show that margin feeds outside the border while
 * padding adds blank framed lines inside it.
 */
@Tag("hardware")
public class HardwarePrintTest {

  private static final File RECEIPT = new File("src/test/resources/printjobs/receipt-full.html");
  private static final File TABLES = new File("src/test/resources/printjobs/tables.html");
  private static final File PAGE_MODE = new File("src/test/resources/printjobs/page-mode.html");
  private static final File PAGE_MODE_ROTATED = new File("src/test/resources/printjobs/page-mode-rotated.html");
  private static final File LABEL = new File("src/test/resources/printjobs/label.html");
  private static final File PAGE_MODE_QR = new File("src/test/resources/printjobs/page-mode-qr.html");
  private static final File ROTATIONS = new File("src/test/resources/printjobs/rotations.html");
  private static final File MIXED_MODE = new File("src/test/resources/printjobs/mixed-mode.html");
  private static final File LINE_HEIGHT = new File("src/test/resources/printjobs/line-height.html");
  private static final File BORDERS = new File("src/test/resources/printjobs/borders.html");
  private static final File FONT_SIZE = new File("src/test/resources/printjobs/font-size.html");
  private static final File LEADER_LINES = new File("src/test/resources/printjobs/leader-lines.html");
  private static final File INDENTATION = new File("src/test/resources/printjobs/indentation.html");
  private static final File SPACING = new File("src/test/resources/printjobs/spacing.html");
  private static final File MARGIN_PADDING = new File("src/test/resources/printjobs/margin-padding.html");
  private static final File SECTION_HEADERS = new File("src/test/resources/printjobs/section-headers.html");
  private static final File ROTATED_CAPTIONS = new File("src/test/resources/printjobs/rotated-captions.html");
  private static final File HEADINGS = new File("src/test/resources/printjobs/headings.html");
  private static final File CHARACTER_EFFECTS = new File("src/test/resources/printjobs/character-effects.html");
  private static final File KANJI = new File("src/test/resources/printjobs/kanji.html");

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsTextReceiptOverUsb() {
    Document doc = Document.from(RECEIPT);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsKanjiReceiptOverUsb() {
    // Mixed Japanese/ASCII receipt: the renderer drops into Kanji mode
    // (FS C 0 / FS & / FS .) for the CJK characters, emitting JIS X 0208 codes,
    // and stays on the code page for ASCII. Requires a TM-T88V with the Japanese
    // Kanji font installed; on a non-Kanji unit the ideographs print blank.
    Document doc = Document.from(KANJI);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsTableShowcaseOverUsb() {
    Document doc = Document.from(TABLES);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsPartialCutPointsOverUsb() {
    // Three receipts split by cuts of each kind, so the bridges can be compared
    // on paper: a one-point partial (GS V 1), a three-point partial (ESC m), and
    // a full cut (GS V 0) at the end. Tear each join and check it holds/severs.
    Document doc = Document.from(
        "<p>Receipt 1 - one point</p><cut points=\"1\"></cut>"
            + "<p>Receipt 2 - three points</p><cut points=\"3\"></cut>"
            + "<p>Receipt 3 - full</p><cut mode=\"full\"></cut>");

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsPageModeCanvasOverUsb() {
    PrinterProfile profile = PrinterProfile.load("TM-T88V").orElseThrow();
    ComputedStyle plain = ComputedStyle.INITIAL;
    ComputedStyle bold = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

    // A fixed-size page-mode region: children placed at exact dot positions.
    Canvas canvas = Canvas.of(profile.dotsPerLine(), 160)
        .place(0, 0, new Paragraph(List.of(new TextRun("PAGE MODE COUPON", bold)), Alignment.LEFT))
        .place(0, 56, new Paragraph(List.of(new TextRun("left @ x=0", plain)), Alignment.LEFT))
        .place(280, 56, new Paragraph(List.of(new TextRun("mid @ x=280", plain)), Alignment.LEFT))
        .place(0, 104, new Paragraph(List.of(new TextRun("bottom @ y=104", plain)), Alignment.LEFT))
        .build();
    byte[] job = new EscPosRenderer(profile).render(List.of(canvas, new Feed(3), new Cut(true)));

    try (UsbTransport transport = UsbTransport.open(0x04b8)) {
      transport.write(job);
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsPageModeHtmlOverUsb() {
    // The full HTML -> IR -> page-mode path: same coupon as the programmatic
    // test, driven from position:relative / position:absolute CSS.
    Document doc = Document.from(PAGE_MODE);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsRotatedPageModeOverUsb() {
    // transform: rotate(180deg) -> ESC T; the coupon should print upside down.
    Document doc = Document.from(PAGE_MODE_ROTATED);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsLabelOverUsb() {
    // Whole-job label: a sized <body> renders as one page-mode area with a name,
    // a role, and a Code 128 barcode. Verify the layout and scan the barcode.
    Document doc = Document.from(LABEL);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsQrPageModeOverUsb() {
    // Diagnostic: is a QR anchored top-down (renderer's assumption) in page mode?
    // The QR (top:56) should sit between the "ABOVE QR" (top:0) and "BELOW QR"
    // (top:260) markers; scan it to confirm 2D renders correctly under ESC L.
    Document doc = Document.from(PAGE_MODE_QR);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsRotationShowcaseOverUsb() {
    // Four page-mode regions in one job: 0, 90, 180 and -90 degrees, each a bold
    // angle label + a QR. Verify each region is rotated as labelled and the QRs scan.
    Document doc = Document.from(ROTATIONS);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsMixedModeOverUsb() {
    // Standard-mode receipt flow (header, table, total, barcode) with an embedded
    // page-mode coupon region (text left, QR right) in a single job. Verify the
    // flow and the positioned region both print, in order.
    Document doc = Document.from(MIXED_MODE);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsLineHeightOverUsb() {
    // line-height -> ESC 3: four wrapped paragraphs (default / 1.0 / 2.0 / 40px).
    // Verify the inter-line spacing visibly differs between them.
    Document doc = Document.from(LINE_HEIGHT);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsBorderedTableOverUsb() {
    // Table grid borders drawn with PC437 box characters: a single-grid items
    // table (with a colspan TOTAL row) and a double-grid note. Verify the lines
    // join up and columns stay aligned.
    Document doc = Document.from(BORDERS);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsHeadingsOverUsb() {
    // Table grid borders drawn with PC437 box characters: a single-grid items
    // table (with a colspan TOTAL row) and a double-grid note. Verify the lines
    // join up and columns stay aligned.
    Document doc = Document.from(HEADINGS);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsFontSizeOverUsb() {
    // CSS font-size -> GS ! magnification: each line should be visibly bigger.
    Document doc = Document.from(FONT_SIZE);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsLeaderLinesOverUsb() {
    // float: right -> leader / space-between lines; verify each value sits flush
    // right, the gap fills (dots for the tax line), and it survives the paper edge.
    Document doc = Document.from(LEADER_LINES);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsIndentationOverUsb() {
    // Block margins (margin-left) and the hanging indent for wrapped list items.
    // Verify the indented note is pushed in and wraps narrower, and each list
    // item's continuation lines align under the text (not the number).
    Document doc = Document.from(INDENTATION);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsSpacingOverUsb() {
    // margin-top / margin-bottom -> dot feeds (ESC J). Verify the first two lines
    // are tight and the later lines have visible gaps above/below.
    Document doc = Document.from(SPACING);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsMarginPaddingOverUsb() {
    // margin vs padding with a border: margin is a blank feed outside the box,
    // padding is blank framed lines inside it. Verify the visible difference in
    // three boxes (margin only / padding only / both).
    Document doc = Document.from(MARGIN_PADDING);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsSectionHeadersOverUsb() {
    // background: black -> full-width reverse-video section headers (white-on-black
    // bars). Verify the whole line is inked (no white gaps at the ends), the label
    // sits where its text-align says, and an inline inverted word is NOT a full bar.
    Document doc = Document.from(SECTION_HEADERS);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsCharacterEffectsOverUsb() {
    // The three newly-wired character effects: double-strike (font-weight 900 ->
    // ESC G, darker than 700), upside-down (transform: rotate(180deg) -> ESC {,
    // the line prints flipped), and smoothing (-webkit-font-smoothing: antialiased
    // -> GS b, smoother edges on the enlarged glyphs). Verify each against its
    // plain counterpart in the print.
    Document doc = Document.from(CHARACTER_EFFECTS);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsRotatedCaptionsOverUsb() {
    // Per-placement rotation in page mode: an upright coupon with captions running
    // down the sides (transform: rotate on absolute children). Verify the side
    // captions are rotated while the upright children stay upright, and note where
    // each caption's (left, top) anchor actually lands (the ESC T anchor pass).
    Document doc = Document.from(ROTATED_CAPTIONS);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.printer.host", matches = ".+")
  void printsTextReceiptOverNetwork() {
    Document doc = Document.from(RECEIPT);
    String host = System.getProperty("faradn.printer.host");

    try (NetworkTransport transport = new NetworkTransport(host)) {
      Printer.print(transport, doc, PrinterProfile.load("TM-T88V").orElseThrow());
    }
  }
}
