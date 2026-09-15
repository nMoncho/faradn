//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Document;
import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.RasterImage;
import net.nmoncho.faradn.document.Barcode;
import net.nmoncho.faradn.document.BarcodeOptions;
import net.nmoncho.faradn.document.BlockLayout;
import net.nmoncho.faradn.document.Border;
import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.Cell;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Cut;
import net.nmoncho.faradn.document.Drawer;
import net.nmoncho.faradn.document.Feed;
import net.nmoncho.faradn.document.ImageBlock;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.Rule;
import net.nmoncho.faradn.document.Table;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.CodePage;
import net.nmoncho.faradn.printer.Font;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.EscPosRenderer;
import net.nmoncho.faradn.printer.Renderer;
import net.nmoncho.faradn.printer.escpos.commands.BarcodeCommands;

/**
 * Golden-byte tests: assert the exact ESC/POS a document renders to. Expected
 * sequences are built from literal command bytes (independent of the command
 * layer's own constants) so a wrong constant is actually caught.
 */
public class EscPosRendererTest {

  private static final byte ESC = 0x1B;
  private static final byte GS = 0x1D;

  private static final byte[] INIT = { ESC, 0x40 };
  private static final byte[] SELECT_PC437 = { ESC, 0x74, 0x00 };
  private static final byte[] HEAD = cat(INIT, SELECT_PC437);
  private static final byte[] LF = { 0x0A };
  private static final byte[] BOLD_ON = { ESC, 0x45, 0x01 };
  private static final byte[] BOLD_OFF = { ESC, 0x45, 0x00 };
  private static final byte[] UNDERLINE_ON = { ESC, 0x2D, 0x01 };
  private static final byte[] UNDERLINE_OFF = { ESC, 0x2D, 0x00 };
  private static final byte[] ITALIC_ON = { ESC, 0x34 };
  private static final byte[] ITALIC_OFF = { ESC, 0x35 };
  private static final byte[] INVERT_ON = { GS, 0x42, 0x01 };
  private static final byte[] INVERT_OFF = { GS, 0x42, 0x00 };
  private static final byte[] DOUBLE_STRIKE_ON = { ESC, 0x47, 0x01 };
  private static final byte[] DOUBLE_STRIKE_OFF = { ESC, 0x47, 0x00 };
  private static final byte[] UPSIDE_ON = { ESC, 0x7B, 0x01 };
  private static final byte[] UPSIDE_OFF = { ESC, 0x7B, 0x00 };
  private static final byte[] SMOOTH_ON = { GS, 0x62, 0x01 };
  private static final byte[] SMOOTH_OFF = { GS, 0x62, 0x00 };
  private static final byte[] ALIGN_CENTER = { ESC, 0x61, 0x01 };
  private static final byte[] ALIGN_RIGHT = { ESC, 0x61, 0x02 };
  private static final byte[] FEED_4 = { ESC, 0x64, 0x04 };
  private static final byte[] PARTIAL_CUT = { GS, 0x56, 0x01 };
  private static final byte[] GS_P_180 = { GS, 0x50, (byte) 180, (byte) 180 };
  private static final byte[] SELECT_PAGE_MODE = { ESC, 0x4C };
  private static final byte[] ESC_T_0 = { ESC, 0x54, 0x00 };
  private static final byte[] ESC_T_1 = { ESC, 0x54, 0x01 };
  private static final byte[] ESC_T_2 = { ESC, 0x54, 0x02 };
  private static final byte[] ESC_T_3 = { ESC, 0x54, 0x03 };
  private static final byte[] FF = { 0x0C };
  private static final byte[] ESC_2 = { ESC, 0x32 };
  private static final byte[] FULL_CUT = { GS, 0x56, 0x00 };
  private static final byte[] DRAWER_2 = { ESC, 0x70, 0x00, 25, (byte) 250 };
  private static final byte[] DRAWER_5 = { ESC, 0x70, 0x01, 25, (byte) 250 };

  // Box-drawing glyphs in PC437 (single / double), for bordered-table goldens.
  private static final byte[] VBAR = { (byte) 0xB3 }; // │
  private static final byte[] DVBAR = { (byte) 0xBA }; // ║
  private static final byte BOX_H = (byte) 0xC4;
  private static final byte BOX_TL = (byte) 0xDA, BOX_TR = (byte) 0xBF, BOX_BL = (byte) 0xC0, BOX_BR = (byte) 0xD9;
  private static final byte BOX_TD = (byte) 0xC2, BOX_TU = (byte) 0xC1;
  private static final byte BOX_TRT = (byte) 0xC3, BOX_TLF = (byte) 0xB4, BOX_X = (byte) 0xC5;
  private static final byte DBOX_H = (byte) 0xCD;
  private static final byte DBOX_TL = (byte) 0xC9, DBOX_TR = (byte) 0xBB, DBOX_BL = (byte) 0xC8, DBOX_BR = (byte) 0xBC;
  private static final byte DBOX_TD = (byte) 0xCB, DBOX_TU = (byte) 0xCA;
  private static final byte DBOX_TRT = (byte) 0xCC, DBOX_TLF = (byte) 0xB9, DBOX_X = (byte) 0xCE;
  private static final byte[] SELECT_FONT_B = { ESC, 0x4D, 0x01 };
  private static final byte[] SELECT_FONT_A = { ESC, 0x4D, 0x00 };
  private static final ComputedStyle FONT_B = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 1);

  private static final PrinterProfile TM_T88V = PrinterProfile.load("TM-T88V").orElseThrow();

  private static final CodePage PC437 = page(0, "IBM437");
  private static final CodePage PC858 = page(19, "IBM00858");
  private static final CodePage WPC1252 = page(16, "windows-1252");
  private static final CodePage PC866 = page(17, "IBM866");
  private static final CodePage PC852 = page(18, "IBM852");

  private final EscPosRenderer renderer = new EscPosRenderer(TM_T88V);
  // A profile with a small, known code-page set for deterministic switching tests.
  private final EscPosRenderer multiPage = new EscPosRenderer(
      PrinterProfile.of("Multi-page", 512, List.of(new Font(0, 42)), 180, true, List.of(PC437, WPC1252, PC866, PC852)));

  @Test
  void plainParagraph() {
    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("Hello", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "Hello", LF, FEED_4, PARTIAL_CUT), out);
  }

  // --- Kanji (multi-byte) text -------------------------------------------------
  // TM-T88V lists CP932, so its profile derives the collision-safe x-JIS0208 Kanji
  // ROM. FS C 0 selects the JIS code system; FS & / FS . bracket each Kanji run.
  private static final byte FS = 0x1C;
  private static final byte[] FS_C_JIS = { FS, 0x43, 0x00 }; // FS C 0
  private static final byte[] FS_KANJI_ON = { FS, 0x26 }; // FS &
  private static final byte[] FS_KANJI_OFF = { FS, 0x2E }; // FS .
  private static final byte[] KAN_KANJI = { 0x34, 0x41 }; // 漢 in JIS X 0208
  private static final byte[] KAN_JI = { 0x3B, 0x7A }; // 字 in JIS X 0208

  @Test
  void kanjiParagraphBracketsCjkAndKeepsAsciiOutsideKanjiMode() {
    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("A漢B", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "A", FS_C_JIS, FS_KANJI_ON, KAN_KANJI, FS_KANJI_OFF, "B", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void kanjiOnlyRunSelectsCodeSystemOnceAndClosesModeAtEnd() {
    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("漢字", ComputedStyle.INITIAL)), Alignment.LEFT)));

    // One FS C 0, one FS &, both glyphs, the paragraph LF, then finish() emits FS .
    assertBytes(cat(HEAD, FS_C_JIS, FS_KANJI_ON, KAN_KANJI, KAN_JI, LF, FS_KANJI_OFF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void cjkWithoutAKanjiRomFallsBackToReplacement() {
    // A profile with no kanjiCharset: 漢 has no encoding and maps to '?'.
    EscPosRenderer noRom = new EscPosRenderer(
        PrinterProfile.of("No-ROM", 512, List.of(new Font(0, 42)), 180, true, List.of(PC437)));
    byte[] out = noRom.render(List.of(
        new Paragraph(List.of(new TextRun("漢", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "?", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void boldRunOnlyTogglesAroundTheBoldRun() {
    ComputedStyle bold = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

    byte[] out = renderer.render(List.of(new Paragraph(List.of(
        new TextRun("Total:", bold),
        new TextRun(" 10", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, BOLD_ON, "Total:", BOLD_OFF, " 10", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void headingEmitsAlignmentBoldAndSize() {
    ComputedStyle h1 = new ComputedStyle(true, false, 2, 2, Alignment.CENTER, false);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("Receipt", h1)), Alignment.CENTER)));

    assertBytes(cat(HEAD, ALIGN_CENTER, BOLD_ON, size(2, 2), "Receipt",
        BOLD_OFF, size(1, 1), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void underlineTogglesAroundTheRun() {
    ComputedStyle underline = new ComputedStyle(false, true, 1, 1, Alignment.LEFT, false);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", underline)), Alignment.LEFT)));

    assertBytes(cat(HEAD, UNDERLINE_ON, "x", UNDERLINE_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void italicTogglesAroundTheRun() {
    ComputedStyle italic = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, true);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", italic)), Alignment.LEFT)));

    assertBytes(cat(HEAD, ITALIC_ON, "x", ITALIC_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void italicFromEmTag() {
    byte[] out = renderer.render(Document.from("<p>a<em>b</em>c</p>").blocks());

    assertBytes(cat(HEAD, "a", ITALIC_ON, "b", ITALIC_OFF, "c", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void doubleStrikeTogglesAroundTheRun() {
    ComputedStyle ds = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false, true, false, false);

    byte[] out = renderer.render(List.of(new Paragraph(List.of(new TextRun("x", ds)), Alignment.LEFT)));

    assertBytes(cat(HEAD, DOUBLE_STRIKE_ON, "x", DOUBLE_STRIKE_OFF, LF, FEED_4, PARTIAL_CUT), out); // ESC G
  }

  @Test
  void upsideDownTogglesAroundTheRun() {
    ComputedStyle ud = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false, false, true, false);

    byte[] out = renderer.render(List.of(new Paragraph(List.of(new TextRun("x", ud)), Alignment.LEFT)));

    assertBytes(cat(HEAD, UPSIDE_ON, "x", UPSIDE_OFF, LF, FEED_4, PARTIAL_CUT), out); // ESC {
  }

  @Test
  void smoothingTogglesAroundTheRun() {
    ComputedStyle sm = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false, false, false, true);

    byte[] out = renderer.render(List.of(new Paragraph(List.of(new TextRun("x", sm)), Alignment.LEFT)));

    assertBytes(cat(HEAD, SMOOTH_ON, "x", SMOOTH_OFF, LF, FEED_4, PARTIAL_CUT), out); // GS b
  }

  @Test
  void heaviestFontWeightAddsDoubleStrikeOnTopOfBold() {
    // font-weight: 900 -> bold (ESC E) + double-strike (ESC G); 700 stays plain bold.
    byte[] out = renderer.render(Document.from("<p style=\"font-weight: 900\">HEAVY</p>").blocks());

    assertBytes(cat(HEAD, BOLD_ON, DOUBLE_STRIKE_ON, "HEAVY", BOLD_OFF, DOUBLE_STRIKE_OFF,
        LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void halfTurnTransformPrintsUpsideDown() {
    byte[] out = renderer.render(Document.from("<p style=\"transform: rotate(180deg)\">FLIP</p>").blocks());

    assertBytes(cat(HEAD, UPSIDE_ON, "FLIP", UPSIDE_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void webkitFontSmoothingMapsToSmoothing() {
    byte[] out = renderer.render(
        Document.from("<p style=\"-webkit-font-smoothing: antialiased\">SMOOTH</p>").blocks());

    assertBytes(cat(HEAD, SMOOTH_ON, "SMOOTH", SMOOTH_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void invertTogglesAroundTheRun() {
    ComputedStyle invert = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, true);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", invert)), Alignment.LEFT)));

    assertBytes(cat(HEAD, INVERT_ON, "x", INVERT_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void filledBannerInksTheWholeLine() {
    ComputedStyle invert = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, true);
    Paragraph banner = new Paragraph(List.of(new TextRun("TOTAL", invert)), Alignment.LEFT, Border.NONE,
        BlockLayout.NONE, true);

    byte[] out = renderer.render(List.of(banner));

    // invert on, the label, then pad to the full width under invert, then off.
    assertBytes(cat(HEAD, INVERT_ON, "TOTAL", " ".repeat(TM_T88V.columns() - 5), INVERT_OFF, LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void filledBannerCentersTheLabel() {
    ComputedStyle invert = new ComputedStyle(false, false, 1, 1, Alignment.CENTER, true);
    Paragraph banner = new Paragraph(List.of(new TextRun("TOTAL", invert)), Alignment.CENTER, Border.NONE,
        BlockLayout.NONE, true);

    byte[] out = renderer.render(List.of(banner));

    int pad = TM_T88V.columns() - 5; // 37 -> 18 left, 19 right
    assertBytes(cat(HEAD, INVERT_ON, " ".repeat(pad / 2), "TOTAL", " ".repeat(pad - pad / 2), INVERT_OFF, LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void rightAlignmentEmitsEscA() {
    ComputedStyle right = new ComputedStyle(false, false, 1, 1, Alignment.RIGHT, false);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", right)), Alignment.RIGHT)));

    assertBytes(cat(HEAD, ALIGN_RIGHT, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void ruleFillsTheLineWidth() {
    byte[] out = renderer.render(List.of(new Rule()));

    assertBytes(cat(HEAD, "-".repeat(TM_T88V.columns()), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void feedBlockEmitsPrintAndFeedLines() {
    byte[] out = renderer.render(List.of(new Feed(3)));

    assertBytes(cat(HEAD, feed(3), FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fullCutBlock() {
    byte[] out = renderer.render(List.of(new Cut(false)));

    // An explicit trailing Cut suppresses the renderer's own end-of-job cut.
    assertBytes(cat(HEAD, FULL_CUT), out);
  }

  @Test
  void partialCutBlock() {
    byte[] out = renderer.render(List.of(new Cut(true)));

    assertBytes(cat(HEAD, PARTIAL_CUT), out);
  }

  @Test
  void drawerBlockPulsesPin2() {
    byte[] out = renderer.render(List.of(new Drawer(2)));

    assertBytes(cat(HEAD, DRAWER_2, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void drawerBlockPulsesPin5() {
    byte[] out = renderer.render(List.of(new Drawer(5)));

    assertBytes(cat(HEAD, DRAWER_5, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void endToEndCashDrawerFromHtml() {
    byte[] out = renderer.render(Document.from("<cash-drawer></cash-drawer>").blocks());

    assertBytes(cat(HEAD, DRAWER_2, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void endToEndPartialCutFromHtml() {
    // A trailing <cut> is the document's own end, so it suppresses the auto cut.
    byte[] out = renderer.render(Document.from("<cut></cut>").blocks());

    assertBytes(cat(HEAD, PARTIAL_CUT), out);
  }

  @Test
  void endToEndFullCutFromHtml() {
    byte[] out = renderer.render(Document.from("<cut mode=\"full\"></cut>").blocks());

    assertBytes(cat(HEAD, FULL_CUT), out);
  }

  @Test
  void endToEndFeedFromHtml() {
    // A <feed> is not a cut, so the renderer still frames the job end (feed + cut).
    byte[] out = renderer.render(Document.from("<feed lines=\"3\"></feed>").blocks());

    assertBytes(cat(HEAD, feed(3), FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void midDocumentCutSeparatesTwoReceipts() {
    byte[] out = renderer.render(Document.from("<p>a</p><cut></cut><p>b</p>").blocks());

    assertBytes(cat(HEAD, "a", LF, PARTIAL_CUT, "b", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void emptyDocumentStillFramesTheJob() {
    byte[] out = renderer.render(List.of());

    assertBytes(cat(HEAD, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void exposesTheRendererInterface() {
    // Callers can depend on the Renderer seam rather than the concrete class.
    Renderer asInterface = new EscPosRenderer(TM_T88V);

    assertBytes(cat(HEAD, FEED_4, PARTIAL_CUT), asInterface.render(List.of()));
  }

  @Test
  void endToEndBoldFromHtml() {
    byte[] out = renderer.render(Document.from("<p>a<b>b</b></p>").blocks());

    assertBytes(cat(HEAD, "a", BOLD_ON, "b", BOLD_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void endToEndCenterFromHtml() {
    byte[] out = renderer.render(Document.from("<center>Hi</center>").blocks());

    assertBytes(cat(HEAD, ALIGN_CENTER, "Hi", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void spanStylesInlineSectionFromHtml() {
    byte[] out = renderer.render(
        Document.from("<p>a<span style=\"font-weight: bold; text-decoration: underline\">b</span>c</p>").blocks());

    assertBytes(cat(HEAD, "a", BOLD_ON, UNDERLINE_ON, "b", BOLD_OFF, UNDERLINE_OFF, "c",
        LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void longWordHardWrapsAtTheColumnBudget() {
    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x".repeat(50), ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "x".repeat(42), LF, "x".repeat(8), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void wrapsAtSpaces() {
    String text = "a".repeat(25) + " " + "b".repeat(25);
    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun(text, ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "a".repeat(25), LF, "b".repeat(25), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void selectsTheProfilesCodePage() {
    byte[] out = new EscPosRenderer(profile(42, PC858)).render(List.of(
        new Paragraph(List.of(new TextRun("x", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(INIT, new byte[] { ESC, 0x74, 0x13 }, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void imageBlockRastersToGsV0() {
    RasterImage img = solid(8, 8, 0xFF000000);
    byte[] raster = ImageRasterizer.raster(img, TM_T88V.dotsPerLine());

    byte[] out = renderer.render(List.of(new ImageBlock(Image.of(img), Alignment.LEFT)));

    assertBytes(cat(HEAD, raster, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void barcodeRendersViaBarcodeCommands() {
    byte[] barcode = BarcodeCommands.encode("ean13", "123456789012");

    byte[] out = renderer.render(List.of(new Barcode("123456789012", "ean13", Alignment.LEFT)));

    assertBytes(cat(HEAD, barcode, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableLaysOutOnACharacterGrid() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    Cell left = new Cell(List.of(new TextRun("ab", plain)), Alignment.LEFT);
    Cell right = new Cell(List.of(new TextRun("cd", plain)), Alignment.RIGHT);
    Table table = new Table(List.of(List.of(left, right)));

    byte[] out = new EscPosRenderer(profile(9, PC437)).render(List.of(table));

    // columnWidth = (9 - 1) / 2 = 4: "ab" + 2 pad, gutter, 2 pad + "cd"
    assertBytes(cat(INIT, SELECT_PC437, "ab", "  ", " ", "  ", "cd", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableFromHtmlLeftAlignsCells() {
    byte[] out = new EscPosRenderer(profile(9, PC437))
        .render(Document.from("<table><tr><td>ab</td><td>cd</td></tr></table>").blocks());

    assertBytes(cat(INIT, SELECT_PC437, "ab", "  ", " ", "cd", "  ", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableCellPreservesInlineStyling() {
    byte[] out = new EscPosRenderer(profile(3, PC437))
        .render(Document.from("<table><tr><td>a<b>b</b>c</td></tr></table>").blocks());

    assertBytes(cat(HEAD, "a", BOLD_ON, "b", BOLD_OFF, "c", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableHeaderCellIsBold() {
    byte[] out = new EscPosRenderer(profile(2, PC437))
        .render(Document.from("<table><tr><th>ab</th></tr></table>").blocks());

    assertBytes(cat(HEAD, BOLD_ON, "ab", BOLD_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableCellSpansColumnsWithColspan() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    List<Cell> header = List.of(
        new Cell(List.of(new TextRun("ab", plain)), Alignment.LEFT),
        new Cell(List.of(new TextRun("cd", plain)), Alignment.LEFT));
    List<Cell> spanning = List.of(new Cell(List.of(new TextRun("wide", plain)), Alignment.LEFT, 2));
    Table table = new Table(List.of(header, spanning));

    byte[] out = new EscPosRenderer(profile(9, PC437)).render(List.of(table));

    // Columns are 4 wide; the colspan=2 cell occupies both plus the gutter (9).
    assertBytes(cat(HEAD, "ab", "  ", " ", "cd", "  ", LF, "wide", "     ", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableSizesColumnsToContent() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    Cell wide = new Cell(List.of(new TextRun("xxxxx", plain)), Alignment.LEFT);
    Cell narrow = new Cell(List.of(new TextRun("y", plain)), Alignment.RIGHT);
    Table table = new Table(List.of(List.of(wide, narrow)));

    byte[] out = new EscPosRenderer(profile(12, PC437)).render(List.of(table));

    // Widths track content (10 vs 1), not an even split - the wide column fills the line.
    assertBytes(cat(HEAD, "xxxxx", "     ", " ", "y", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void textReceiptFixtureRendersFramedJob() {
    byte[] out = renderer.render(
        Document.from(new File("src/test/resources/printjobs/receipt-text.html")).blocks());

    assertArrayEquals(INIT, Arrays.copyOfRange(out, 0, INIT.length));
    assertArrayEquals(PARTIAL_CUT, Arrays.copyOfRange(out, out.length - PARTIAL_CUT.length, out.length));
  }

  @Test
  void fullReceiptFixtureRendersEndToEnd() {
    byte[] out = renderer.render(
        Document.from(new File("src/test/resources/printjobs/receipt-full.html")).blocks());

    assertArrayEquals(INIT, Arrays.copyOfRange(out, 0, INIT.length));
    assertArrayEquals(PARTIAL_CUT, Arrays.copyOfRange(out, out.length - PARTIAL_CUT.length, out.length));
    assertTrue(out.length > 200, "full receipt should exercise image, table, barcodes and wrapping");
  }

  @Test
  void unorderedListEmitsMarkers() {
    byte[] out = renderer.render(Document.from("<ul><li>a</li><li>b</li></ul>").blocks());

    assertBytes(cat(HEAD, "- a", LF, "- b", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void orderedListNumbersItems() {
    byte[] out = renderer.render(Document.from("<ol><li>a</li><li>b</li></ol>").blocks());

    assertBytes(cat(HEAD, "1. a", LF, "2. b", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void preformattedPreservesWhitespace() {
    byte[] out = renderer.render(Document.from("<pre>a  b\n  c</pre>").blocks());

    assertBytes(cat(HEAD, "a  b", LF, "  c", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableCenterAlignsCells() {
    Cell centered = new Cell(List.of(new TextRun("ab", ComputedStyle.INITIAL)), Alignment.CENTER);
    Table table = new Table(List.of(List.of(centered)));

    byte[] out = new EscPosRenderer(profile(6, PC437)).render(List.of(table));

    assertBytes(cat(INIT, SELECT_PC437, "  ", "ab", "  ", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void dynamicCodePageSwitchesForGlyphOutsideDefault() {
    // "a€b": the euro is absent from PC437, present in WPC1252 (id 16).
    byte[] out = multiPage.render(List.of(
        new Paragraph(List.of(new TextRun("a€b", ComputedStyle.INITIAL)), Alignment.LEFT)));

    byte[] selectWpc1252 = { ESC, 0x74, 16 };
    byte[] euro = "€".getBytes(WPC1252.charset());
    // 'b' stays on WPC1252 (no needless switch back), still encoding to ASCII 0x62.
    assertBytes(cat(HEAD, "a", selectWpc1252, euro, "b", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void dynamicCodePageSwitchesOncePerRun() {
    // "Жи": both Cyrillic, only PC866 (id 17) encodes them - a single switch.
    byte[] out = multiPage.render(List.of(
        new Paragraph(List.of(new TextRun("Жи", ComputedStyle.INITIAL)), Alignment.LEFT)));

    byte[] selectPc866 = { ESC, 0x74, 17 };
    byte[] cyrillic = "Жи".getBytes(PC866.charset());
    assertBytes(cat(HEAD, selectPc866, cyrillic, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void dynamicCodePageSwitchesBackWhenCurrentCannotEncode() {
    // "Жé": Cyrillic forces PC866, then 'é' (absent there) switches back to PC437.
    byte[] out = multiPage.render(List.of(
        new Paragraph(List.of(new TextRun("Жé", ComputedStyle.INITIAL)), Alignment.LEFT)));

    byte[] cyrillic = "Ж".getBytes(PC866.charset());
    byte[] eAcute = "é".getBytes(PC437.charset());
    assertBytes(cat(HEAD, new byte[] { ESC, 0x74, 17 }, cyrillic, new byte[] { ESC, 0x74, 0 }, eAcute, LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void unmappableGlyphFallsBackToReplacement() {
    // "中" is in none of the supported pages: it stays on PC437 and encodes to '?'.
    byte[] out = multiPage.render(List.of(
        new Paragraph(List.of(new TextRun("中", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "?", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fontBParagraphSelectsFontAndWrapsWide() {
    // Font B fits 8 columns where Font A fits 4, so the 8-char run stays on one line.
    byte[] out = new EscPosRenderer(fontBProfile(4, 8)).render(List.of(
        new Paragraph(List.of(new TextRun("abcdefgh", FONT_B)), Alignment.LEFT)));

    assertBytes(cat(HEAD, SELECT_FONT_B, "abcdefgh", SELECT_FONT_A, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void smallElementRendersInFontB() {
    byte[] out = new EscPosRenderer(fontBProfile(42, 56))
        .render(Document.from("<small>hi</small>").blocks());

    assertBytes(cat(HEAD, SELECT_FONT_B, "hi", SELECT_FONT_A, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void mixedFontParagraphSwitchesPerRun() {
    byte[] out = new EscPosRenderer(fontBProfile(42, 56))
        .render(Document.from("<p>a<small>b</small></p>").blocks());

    assertBytes(cat(HEAD, "a", SELECT_FONT_B, "b", SELECT_FONT_A, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fontBTableUsesNarrowColumnBudget() {
    // The cell is Font B, so the table uses the wider Font B budget (6, not 2):
    // "abc" fits with three trailing pad instead of overflowing a 2-wide column.
    byte[] out = new EscPosRenderer(fontBProfile(2, 6))
        .render(Document.from("<table><tr><td><small>abc</small></td></tr></table>").blocks());

    assertBytes(cat(HEAD, SELECT_FONT_B, "abc", SELECT_FONT_A, "   ", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fontFamilyCssMakesWholeTableFontB() {
    // font-family on the table applies Font B to every cell (which <small> can't wrap).
    byte[] out = new EscPosRenderer(fontBProfile(2, 6))
        .render(Document.from("<table style=\"font-family: font-b\"><tr><td>abc</td></tr></table>").blocks());

    assertBytes(cat(HEAD, SELECT_FONT_B, "abc", SELECT_FONT_A, "   ", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fontCSelectedByCssUsesItsColumnBudget() {
    // A 3-font printer: font-c is slot 2 (ESC M 2) with a 10-column budget.
    PrinterProfile threeFonts = PrinterProfile.of("three", 512,
        List.of(new Font(0, 4), new Font(1, 6), new Font(2, 10)), 180, true, List.of(PC437));

    byte[] out = new EscPosRenderer(threeFonts)
        .render(Document.from("<p style=\"font-family: font-c\">abcdefghij</p>").blocks());

    byte[] selectFontC = { ESC, 0x4D, 0x02 };
    assertBytes(cat(HEAD, selectFontC, "abcdefghij", SELECT_FONT_A, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasEmitsPageModeSequence() {
    Canvas canvas = Canvas.of(512, 160)
        .place(100, 40, new Paragraph(List.of(new TextRun("Hi", ComputedStyle.INITIAL)), Alignment.LEFT))
        .build();

    byte[] out = renderer.render(List.of(canvas));

    // GS $ is y + one Font A cell (512/42 -> 12 wide, x2 = 24 tall) so the text's
    // top - not its baseline - lands at y=40.
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 160), ESC_T_0,
        escDollar(100), gsDollar(64), "Hi", FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasPlacesEachChildAbsolutely() {
    Canvas canvas = Canvas.of(384, 120)
        .place(0, 0, new Paragraph(List.of(new TextRun("A", ComputedStyle.INITIAL)), Alignment.LEFT))
        .place(200, 0, new Paragraph(List.of(new TextRun("B", ComputedStyle.INITIAL)), Alignment.LEFT))
        .build();

    byte[] out = renderer.render(List.of(canvas));

    // Both texts sit at y=0; GS $ = 0 + 24 (one Font A cell) drops the baseline.
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(384, 120), ESC_T_0,
        escDollar(0), gsDollar(24), "A",
        escDollar(200), gsDollar(24), "B",
        FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasResetsInlineStyleBetweenPlacements() {
    ComputedStyle bold = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);
    Canvas canvas = Canvas.of(384, 120)
        .place(0, 0, new Paragraph(List.of(new TextRun("A", bold)), Alignment.LEFT))
        .place(0, 40, new Paragraph(List.of(new TextRun("b", ComputedStyle.INITIAL)), Alignment.LEFT))
        .build();

    byte[] out = renderer.render(List.of(canvas));

    // "A" turns bold on; the next placement clears it before "b".
    // GS $ carries the +24 (one Font A cell) baseline drop on both texts.
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(384, 120), ESC_T_0,
        escDollar(0), gsDollar(24), BOLD_ON, "A",
        escDollar(0), gsDollar(64), BOLD_OFF, "b",
        FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasPlacesImageAndBarcode() {
    RasterImage img = solid(8, 8, 0xFF000000);
    byte[] raster = ImageRasterizer.raster(img, 512);
    byte[] barcode = BarcodeCommands.encode("code128", "12345678");

    Canvas canvas = Canvas.of(512, 200)
        .place(0, 0, new ImageBlock(Image.of(img), Alignment.LEFT))
        .place(0, 96, new Barcode("12345678", "code128", Alignment.LEFT))
        .build();

    byte[] out = renderer.render(List.of(canvas));

    // The image develops downward from y (no offset); the 1D barcode's bars draw
    // upward from GS $, so it drops by its bar height (default 100) to put its top at y=96.
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 200), ESC_T_0,
        escDollar(0), gsDollar(0), raster,
        escDollar(0), gsDollar(196), barcode,
        FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasOffsets1dBarcodeByBarHeightButNot2d() {
    // 1D: bars drawn upward -> GS $ = y + height (60). 2D: QR develops downward -> GS $ = y.
    byte[] oneD = renderer.render(List.of(Canvas.of(512, 300)
        .place(0, 40, new Barcode("12345678", "code128", Alignment.LEFT,
            new BarcodeOptions(60, 0, BarcodeOptions.Hri.BELOW, BarcodeOptions.QrEc.M)))
        .build()));
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 300), ESC_T_0,
        escDollar(0), gsDollar(100), BarcodeCommands.encode("code128", "12345678",
            new BarcodeOptions(60, 0, BarcodeOptions.Hri.BELOW, BarcodeOptions.QrEc.M)),
        FF, FEED_4, PARTIAL_CUT), oneD);

    byte[] twoD = renderer.render(List.of(Canvas.of(512, 300)
        .place(0, 40, new Barcode("HELLO", "qr", Alignment.LEFT)).build()));
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 300), ESC_T_0,
        escDollar(0), gsDollar(40), BarcodeCommands.encode("qr", "HELLO"),
        FF, FEED_4, PARTIAL_CUT), twoD);
  }

  @Test
  void canvasBaselineDropScalesWithFontAndHeight() {
    // Double-height Font A: cell = (512/42 -> 12) x 2 x heightMultiple(2) = 48.
    ComputedStyle tall = new ComputedStyle(false, false, 1, 2, Alignment.LEFT, false, 0);
    byte[] tallOut = renderer.render(List.of(Canvas.of(512, 200)
        .place(0, 0, new Paragraph(List.of(new TextRun("T", tall)), Alignment.LEFT)).build()));
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 200), ESC_T_0,
        escDollar(0), gsDollar(48), size(1, 2), "T", size(1, 1), FF, FEED_4, PARTIAL_CUT), tallOut);

    // Font B is narrower: cell = (512/56 -> 9) x 2 = 18.
    byte[] fontBOut = renderer.render(List.of(Canvas.of(512, 200)
        .place(0, 0, new Paragraph(List.of(new TextRun("b", FONT_B)), Alignment.LEFT)).build()));
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 200), ESC_T_0,
        escDollar(0), gsDollar(18), SELECT_FONT_B, "b", SELECT_FONT_A, FF, FEED_4, PARTIAL_CUT), fontBOut);
  }

  @Test
  void canvasReissuesEscTForARotatedPlacementAndRestores() {
    Canvas canvas = Canvas.of(512, 160)
        .place(0, 0, new Paragraph(List.of(new TextRun("A", ComputedStyle.INITIAL)), Alignment.LEFT))
        .place(480, 8, new Paragraph(List.of(new TextRun("VOID", ComputedStyle.INITIAL)), Alignment.LEFT),
            Canvas.Direction.ROTATE_90_CW)
        .place(0, 120, new Paragraph(List.of(new TextRun("z", ComputedStyle.INITIAL)), Alignment.LEFT))
        .build();

    byte[] out = renderer.render(List.of(canvas));

    // "A" is upright: ESC $ = x, GS $ = y + one Font A cell (24). "VOID" switches
    // to ESC T 3 (top-to-bottom, 90 CW) and its upright (480, 8) maps into that
    // frame as (y, w - x) = (8, 32) with no baseline drop. "z" restores ESC T 0
    // and is upright again at (0, 120 + 24).
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 160), ESC_T_0,
        escDollar(0), gsDollar(24), "A",
        ESC_T_3, escDollar(8), gsDollar(32), "VOID",
        ESC_T_0, escDollar(0), gsDollar(144), "z",
        FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasMapsCounterClockwisePlacementToLeftEdge() {
    // rotate(-90deg) = ESC T 1 (bottom-to-top): an upright (x, y) maps to
    // (h - y, x). x drives GS $, so a left-edge caption (x=8) stays at the left
    // (GS $ = 8) instead of drifting to mid-width.
    Canvas canvas = Canvas.of(512, 220)
        .place(8, 200, new Paragraph(List.of(new TextRun("S", ComputedStyle.INITIAL)), Alignment.LEFT),
            Canvas.Direction.ROTATE_90_CCW)
        .build();

    byte[] out = renderer.render(List.of(canvas));

    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 220), ESC_T_0,
        ESC_T_1, escDollar(20), gsDollar(8), "S", // (h - y, x) = (220 - 200, 8)
        FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasKeepsOneEscTForConsecutiveSameDirectionPlacements() {
    // Two rotated placements in a row emit ESC T once, not per placement.
    Canvas canvas = Canvas.of(512, 160)
        .place(400, 0, new Paragraph(List.of(new TextRun("a", ComputedStyle.INITIAL)), Alignment.LEFT),
            Canvas.Direction.ROTATE_90_CW)
        .place(440, 0, new Paragraph(List.of(new TextRun("b", ComputedStyle.INITIAL)), Alignment.LEFT),
            Canvas.Direction.ROTATE_90_CW)
        .build();

    byte[] out = renderer.render(List.of(canvas));

    // Both rotate 90 CW (ESC T 3): (400, 0) -> (y, w - x) = (0, 112); (440, 0) -> (0, 72).
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 160), ESC_T_0,
        ESC_T_3, escDollar(0), gsDollar(112), "a",
        escDollar(0), gsDollar(72), "b", // same direction: no second ESC T
        FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void htmlPositionedContainerRendersPageMode() {
    // End-to-end: HTML position:relative container + absolute children -> Canvas -> page mode.
    byte[] out = renderer.render(Document.from(
        "<div style=\"position: relative; width: 512px; height: 120px\">"
            + "<span style=\"position: absolute; left: 0; top: 0\">A</span>"
            + "<span style=\"position: absolute; left: 200px; top: 60px\">B</span>"
            + "</div>")
        .blocks(180));

    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 120), ESC_T_0,
        escDollar(0), gsDollar(24), "A", // y=0 + one Font A cell (24)
        escDollar(200), gsDollar(84), "B", // y=60 + 24
        FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasDirectionSelectsEscT() {
    assertEscTForDirection(Canvas.Direction.NORMAL, ESC_T_0);
    assertEscTForDirection(Canvas.Direction.ROTATE_90_CW, ESC_T_3);
    assertEscTForDirection(Canvas.Direction.ROTATE_180, ESC_T_2);
    assertEscTForDirection(Canvas.Direction.ROTATE_90_CCW, ESC_T_1);
  }

  private void assertEscTForDirection(Canvas.Direction direction, byte[] escT) {
    byte[] out = renderer.render(List.of(Canvas.of(512, 120).direction(direction)
        .place(0, 0, new Paragraph(List.of(new TextRun("X", ComputedStyle.INITIAL)), Alignment.LEFT)).build()));
    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 120), escT,
        escDollar(0), gsDollar(24), "X", FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void canvasStillRendersOnProfileWithoutPageMode() {
    // supportsPageMode() == false only warns; the page-mode bytes are still emitted (best effort).
    EscPosRenderer noPageMode = new EscPosRenderer(withoutPageMode(TM_T88V));
    byte[] out = noPageMode.render(List.of(Canvas.of(512, 120)
        .place(0, 0, new Paragraph(List.of(new TextRun("X", ComputedStyle.INITIAL)), Alignment.LEFT)).build()));

    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 120), ESC_T_0,
        escDollar(0), gsDollar(24), "X", FF, FEED_4, PARTIAL_CUT), out);
  }

  private static PrinterProfile withoutPageMode(PrinterProfile base) {
    return new PrinterProfile() {
      @Override
      public String name() {
        return base.name();
      }

      @Override
      public int dotsPerLine() {
        return base.dotsPerLine();
      }

      @Override
      public List<Font> fonts() {
        return base.fonts();
      }

      @Override
      public int dpi() {
        return base.dpi();
      }

      @Override
      public boolean supportsCut() {
        return base.supportsCut();
      }

      @Override
      public CodePage codePage() {
        return base.codePage();
      }

      @Override
      public List<CodePage> codePages() {
        return base.codePages();
      }

      @Override
      public boolean supportsPageMode() {
        return false;
      }
    };
  }

  @Test
  void htmlSizedBodyRendersWholeJobPageMode() {
    // A sized <body> makes the whole job one page-mode label (single ESC L … FF).
    byte[] out = renderer.render(Document.from(
        "<body style=\"width: 512px; height: 300px\">"
            + "<span style=\"position: absolute; left: 0; top: 0\">LABEL</span></body>")
        .blocks(180));

    assertBytes(cat(HEAD, GS_P_180, SELECT_PAGE_MODE, escW(512, 300), ESC_T_0,
        escDollar(0), gsDollar(24), "LABEL", FF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void htmlInterleavesStandardAndPageMode() {
    // A standard-mode paragraph, an embedded page-mode region, then another
    // standard-mode paragraph - all framed as one job.
    byte[] out = renderer.render(Document.from(
        "<p>Hi</p>"
            + "<div style=\"position: relative; width: 512px; height: 80px\">"
            + "<span style=\"position: absolute; left: 0; top: 0\">X</span></div>"
            + "<p>Bye</p>")
        .blocks(180));

    assertBytes(cat(HEAD,
        "Hi", LF,
        GS_P_180, SELECT_PAGE_MODE, escW(512, 80), ESC_T_0, escDollar(0), gsDollar(24), "X", FF,
        "Bye", LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void lineHeightBracketsParagraphWithEscThreeAndTwo() {
    // Font A cell = 24; line-height 2 -> ESC 3 48. GS P pins the motion unit to
    // dots first (so ESC 3 is dot-based); ESC 2 restores the default after.
    byte[] out = renderer.render(Document.from("<p style=\"line-height: 2\">hi</p>").blocks());

    assertBytes(cat(HEAD, GS_P_180, esc3(48), "hi", LF, ESC_2, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void lineHeightInPixelsIsDotsOneToOne() {
    byte[] out = renderer.render(Document.from("<p style=\"line-height: 40px\">hi</p>").blocks());

    assertBytes(cat(HEAD, GS_P_180, esc3(40), "hi", LF, ESC_2, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fontSizeEmitsCharacterSize() {
    // font-size: 200% -> 2x width and height via GS ! (0x11), reset to 1x after.
    byte[] out = renderer.render(Document.from("<p style=\"font-size: 200%\">hi</p>").blocks());

    assertBytes(cat(HEAD, size(2, 2), "hi", size(1, 1), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fontShorthandDrivesBoldSizeAndItalic() {
    // font: italic bold 200% font-a -> bold + 2x (GS !) + italic, reset after.
    byte[] out = renderer.render(Document.from("<p style=\"font: italic bold 200% font-a\">hi</p>").blocks());

    assertBytes(cat(HEAD, BOLD_ON, size(2, 2), ITALIC_ON, "hi",
        BOLD_OFF, size(1, 1), ITALIC_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void normalLineHeightEmitsNoSpacingCommands() {
    byte[] out = renderer.render(Document.from("<p>hi</p>").blocks());

    assertBytes(cat(HEAD, "hi", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void borderedTableDrawsSingleGrid() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    Table table = new Table(List.of(
        List.of(new Cell(List.of(new TextRun("ab", plain)), Alignment.LEFT),
            new Cell(List.of(new TextRun("cd", plain)), Alignment.LEFT)),
        List.of(new Cell(List.of(new TextRun("ef", plain)), Alignment.LEFT),
            new Cell(List.of(new TextRun("gh", plain)), Alignment.LEFT))),
        Border.all(Border.Style.SINGLE), true);

    byte[] out = new EscPosRenderer(profile(7, PC437)).render(List.of(table));

    // budget = 7 - (columns+1=3) = 4 -> widths [2, 2]
    assertBytes(cat(HEAD,
        boxRule(BOX_H, BOX_TL, BOX_TD, BOX_TR, 2, 2), LF,
        VBAR, "ab", VBAR, "cd", VBAR, LF,
        boxRule(BOX_H, BOX_TRT, BOX_X, BOX_TLF, 2, 2), LF,
        VBAR, "ef", VBAR, "gh", VBAR, LF,
        boxRule(BOX_H, BOX_BL, BOX_TU, BOX_BR, 2, 2), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void borderedTableDrawsDoubleGrid() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    Table table = new Table(List.of(
        List.of(new Cell(List.of(new TextRun("ab", plain)), Alignment.LEFT),
            new Cell(List.of(new TextRun("cd", plain)), Alignment.LEFT))),
        Border.all(Border.Style.DOUBLE), true);

    byte[] out = new EscPosRenderer(profile(7, PC437)).render(List.of(table));

    assertBytes(cat(HEAD,
        boxRule(DBOX_H, DBOX_TL, DBOX_TD, DBOX_TR, 2, 2), LF,
        DVBAR, "ab", DVBAR, "cd", DVBAR, LF,
        boxRule(DBOX_H, DBOX_BL, DBOX_TU, DBOX_BR, 2, 2), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void borderedTableColspanAtTopSelectsJoinGlyphs() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    Table table = new Table(List.of(
        List.of(new Cell(List.of(new TextRun("hdr", plain)), Alignment.LEFT, 2)),
        List.of(new Cell(List.of(new TextRun("ab", plain)), Alignment.LEFT),
            new Cell(List.of(new TextRun("cd", plain)), Alignment.LEFT))),
        Border.all(Border.Style.SINGLE), true);

    byte[] out = new EscPosRenderer(profile(9, PC437)).render(List.of(table));

    // widths [3, 3]. The span sits in the top row, so no divider meets the top
    // rule from below (plain ─); the separator gains a ┬ as the columns split.
    assertBytes(cat(HEAD,
        boxRule(BOX_H, BOX_TL, BOX_TR, new int[] { 3, 3 }, BOX_H), LF,
        VBAR, "hdr    ", VBAR, LF,
        boxRule(BOX_H, BOX_TRT, BOX_TLF, new int[] { 3, 3 }, BOX_TD), LF,
        VBAR, "ab ", VBAR, "cd ", VBAR, LF,
        boxRule(BOX_H, BOX_BL, BOX_BR, new int[] { 3, 3 }, BOX_TU), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void borderedTableColspanAtBottomSelectsJoinGlyphs() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    // Mirrors a totals row: a colspan over the first two of three columns.
    Table table = new Table(List.of(
        List.of(new Cell(List.of(new TextRun("a", plain)), Alignment.LEFT),
            new Cell(List.of(new TextRun("b", plain)), Alignment.LEFT),
            new Cell(List.of(new TextRun("c", plain)), Alignment.LEFT)),
        List.of(new Cell(List.of(new TextRun("tot", plain)), Alignment.LEFT, 2),
            new Cell(List.of(new TextRun("p", plain)), Alignment.LEFT))),
        Border.all(Border.Style.SINGLE), true);

    byte[] out = new EscPosRenderer(profile(13, PC437)).render(List.of(table));

    // widths [3, 3, 3]. Separator over the span: ┴ at the merged boundary, ┼ at
    // the split; bottom under the span: plain ─ at the merged boundary, ┴ at the split.
    assertBytes(cat(HEAD,
        boxRule(BOX_H, BOX_TL, BOX_TR, new int[] { 3, 3, 3 }, BOX_TD, BOX_TD), LF,
        VBAR, "a  ", VBAR, "b  ", VBAR, "c  ", VBAR, LF,
        boxRule(BOX_H, BOX_TRT, BOX_TLF, new int[] { 3, 3, 3 }, BOX_TU, BOX_X), LF,
        VBAR, "tot    ", VBAR, "p  ", VBAR, LF,
        boxRule(BOX_H, BOX_BL, BOX_BR, new int[] { 3, 3, 3 }, BOX_H, BOX_TU), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void paragraphBorderBottomEmitsRuleBelow() {
    byte[] out = new EscPosRenderer(profile(7, PC437))
        .render(Document.from("<p style=\"border-bottom: 1px solid\">Total</p>").blocks());

    assertBytes(cat(HEAD, "Total", LF, hline(BOX_H, 7), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void paragraphBorderTopEmitsRuleAbove() {
    byte[] out = new EscPosRenderer(profile(7, PC437))
        .render(Document.from("<p style=\"border-top: 1px solid\">Hdr</p>").blocks());

    assertBytes(cat(HEAD, hline(BOX_H, 7), LF, "Hdr", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void paragraphBorderShorthandDrawsFullBox() {
    byte[] out = new EscPosRenderer(profile(7, PC437))
        .render(Document.from("<p style=\"border: 1px solid\">Box</p>").blocks());

    // columns 7, sides eat 2 -> content width 5; "Box" padded to "Box  ".
    assertBytes(cat(HEAD,
        boxEdge(BOX_TL, BOX_H, BOX_TR, 5), LF,
        VBAR, "Box  ", VBAR, LF,
        boxEdge(BOX_BL, BOX_H, BOX_BR, 5), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void boxedParagraphWrapsContentInsideTheFrame() {
    byte[] out = new EscPosRenderer(profile(7, PC437))
        .render(Document.from("<p style=\"border: 1px solid\">hello world</p>").blocks());

    // content width 5: "hello" / "world" each framed by │.
    assertBytes(cat(HEAD,
        boxEdge(BOX_TL, BOX_H, BOX_TR, 5), LF,
        VBAR, "hello", VBAR, LF,
        VBAR, "world", VBAR, LF,
        boxEdge(BOX_BL, BOX_H, BOX_BR, 5), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void boxedParagraphDoubleWeight() {
    byte[] out = new EscPosRenderer(profile(8, PC437))
        .render(Document.from("<div style=\"border: 3px double\">Note</div>").blocks());

    // columns 8 -> content width 6; double corners and rails.
    assertBytes(cat(HEAD,
        boxEdge(DBOX_TL, DBOX_H, DBOX_TR, 6), LF,
        DVBAR, "Note  ", DVBAR, LF,
        boxEdge(DBOX_BL, DBOX_H, DBOX_BR, 6), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void boxedParagraphLeftSideOnly() {
    byte[] out = new EscPosRenderer(profile(7, PC437))
        .render(Document.from("<p style=\"border-left: 1px solid\">Hi</p>").blocks());

    // left rail only: no top/bottom rule, no right rail; content padded to 6.
    assertBytes(cat(HEAD, VBAR, "Hi    ", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void paragraphBorderDoubleUsesDoubleRule() {
    byte[] out = new EscPosRenderer(profile(7, PC437))
        .render(Document.from("<p style=\"border-bottom: 3px double\">Sum</p>").blocks());

    assertBytes(cat(HEAD, "Sum", LF, hline(DBOX_H, 7), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void boxWrapsMultipleParagraphsInOneFrame() {
    byte[] out = new EscPosRenderer(profile(7, PC437))
        .render(Document.from("<div style=\"border: 1px solid\"><p>aa</p><p>bb</p></div>").blocks());

    // one frame around both paragraphs; each line padded to content width 5.
    assertBytes(cat(HEAD,
        boxEdge(BOX_TL, BOX_H, BOX_TR, 5), LF,
        VBAR, "aa   ", VBAR, LF,
        VBAR, "bb   ", VBAR, LF,
        boxEdge(BOX_BL, BOX_H, BOX_BR, 5), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void leaderLineFillsTheGapWithSpaces() {
    byte[] out = new EscPosRenderer(profile(20, PC437))
        .render(Document.from("<p>Subtotal <span style=\"float: right\">9,00</span></p>").blocks());

    // 20 cols - "Subtotal " (9) - "9,00" (4) = 7 spaces of gap.
    assertBytes(cat(HEAD, "Subtotal " + " ".repeat(7) + "9,00", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void leaderLineDottedFill() {
    byte[] out = new EscPosRenderer(profile(20, PC437)).render(Document.from(
        "<p>Total <span style=\"float: right\" data-leader=\".\">12,50</span></p>").blocks());

    // 20 - "Total " (6) - "12,50" (5) = 9 dots.
    assertBytes(cat(HEAD, "Total " + ".".repeat(9) + "12,50", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void leaderLineOverflowDropsRightToItsOwnLine() {
    byte[] out = new EscPosRenderer(profile(10, PC437)).render(Document.from(
        "<p>A very long label here <span style=\"float: right\">9999</span></p>").blocks());

    // Doesn't fit: left on its own line, right group on its own right-aligned line.
    assertBytes(cat(HEAD, "A very long label here ", LF, ALIGN_RIGHT, "9999", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void marginLeftIndentsAndNarrowsTheWrap() {
    byte[] out = new EscPosRenderer(profile(14, PC437))
        .render(Document.from("<p style=\"margin-left: 3ch\">indent me please over lines</p>").blocks());

    // every line padded 3, content wrapped to 14 - 3 = 11.
    assertBytes(cat(HEAD, "   indent me", LF, "   please over", LF, "   lines", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void wrappedListItemHangsUnderTheText() {
    byte[] out = new EscPosRenderer(profile(14, PC437))
        .render(Document.from("<ol><li>first item that wraps over lines</li></ol>").blocks());

    // first line at the marker; continuations hang in by the marker width (3).
    assertBytes(cat(HEAD, "1. first item", LF, "   that wraps", LF, "   over lines", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void marginTopFeedsDotsBeforeTheBlock() {
    byte[] out = new EscPosRenderer(profile(20, PC437))
        .render(Document.from("<p style=\"margin-top: 24px\">a</p>").blocks());

    // GS P pins the unit to dots, then ESC J 24 feeds 24 dots before the paragraph.
    assertBytes(cat(HEAD, GS_P_180, escJ(24), "a", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void marginBottomFeedsDotsAfterTheBlock() {
    byte[] out = new EscPosRenderer(profile(20, PC437))
        .render(Document.from("<p style=\"margin-bottom: 24px\">a</p>").blocks());

    assertBytes(cat(HEAD, "a", LF, GS_P_180, escJ(24), FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void paddingInsideABoxIsBlankFramedLines() {
    byte[] out = new EscPosRenderer(profile(8, PC437))
        .render(Document.from("<div style=\"border: 1px solid; padding-top: 30px\"><p>ab</p></div>").blocks());

    // content width 6; padding-top 30px -> 1 blank framed line (1/6" advance = 30 dots at 180 dpi).
    assertBytes(cat(HEAD,
        boxEdge(BOX_TL, BOX_H, BOX_TR, 6), LF,
        VBAR, "      ", VBAR, LF,
        VBAR, "ab    ", VBAR, LF,
        boxEdge(BOX_BL, BOX_H, BOX_BR, 6), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void marginFeedsOutsideAndPaddingFramesInside() {
    byte[] out = new EscPosRenderer(profile(8, PC437)).render(Document.from(
        "<div style=\"border: 1px solid; margin-top: 24px; padding-bottom: 30px\"><p>ab</p></div>").blocks());

    // margin-top: dot feed before the box; padding-bottom: blank framed line inside.
    assertBytes(cat(HEAD,
        GS_P_180, escJ(24),
        boxEdge(BOX_TL, BOX_H, BOX_TR, 6), LF,
        VBAR, "ab    ", VBAR, LF,
        VBAR, "      ", VBAR, LF,
        boxEdge(BOX_BL, BOX_H, BOX_BR, 6), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void nullProfileIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new EscPosRenderer(null));
  }

  // ----- helpers -----

  private static byte[] esc3(int n) {
    return new byte[] { ESC, 0x33, (byte) n }; // ESC 3 n: set line spacing
  }

  private static byte[] escJ(int n) {
    return new byte[] { ESC, 0x4A, (byte) n }; // ESC J n: print and feed n dots
  }

  /** A bare horizontal line of {@code width} box glyphs (a paragraph rule). */
  private static byte[] hline(byte glyph, int width) {
    final byte[] line = new byte[width];
    Arrays.fill(line, glyph);
    return line;
  }

  /**
   * A box edge: {@code left} corner, {@code glyph}×contentWidth, {@code right}
   * corner.
   */
  private static byte[] boxEdge(byte left, byte glyph, byte right, int contentWidth) {
    final ByteArrayOutputStream edge = new ByteArrayOutputStream();
    edge.write(left);
    for (int i = 0; i < contentWidth; i++) {
      edge.write(glyph);
    }
    edge.write(right);
    return edge.toByteArray();
  }

  /**
   * A horizontal box rule: {@code left}, then {@code hz}×width per column joined
   * by {@code join}, then {@code right}.
   */
  private static byte[] boxRule(byte hz, byte left, byte join, byte right, int... widths) {
    final byte[] joins = new byte[Math.max(0, widths.length - 1)];
    Arrays.fill(joins, join);
    return boxRule(hz, left, right, widths, joins);
  }

  /**
   * A horizontal box rule with a per-boundary join glyph (one per interior
   * boundary).
   */
  private static byte[] boxRule(byte hz, byte left, byte right, int[] widths, byte... joins) {
    final ByteArrayOutputStream rule = new ByteArrayOutputStream();
    rule.write(left);
    for (int c = 0; c < widths.length; c++) {
      for (int k = 0; k < widths[c]; k++) {
        rule.write(hz);
      }
      rule.write(c < widths.length - 1 ? joins[c] : right);
    }
    return rule.toByteArray();
  }

  private static byte[] escDollar(int value) {
    return new byte[] { ESC, 0x24, (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF) };
  }

  private static byte[] gsDollar(int value) {
    return new byte[] { GS, 0x24, (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF) };
  }

  private static byte[] escW(int width, int height) {
    return new byte[] { ESC, 0x57, 0, 0, 0, 0,
        (byte) (width & 0xFF), (byte) ((width >> 8) & 0xFF),
        (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF) };
  }

  private static PrinterProfile profile(int columns, CodePage codePage) {
    return PrinterProfile.of("test", 512, List.of(new Font(0, columns)), 180, true, List.of(codePage));
  }

  private static PrinterProfile fontBProfile(int columns, int narrowColumns) {
    return PrinterProfile.of("fontb", 512, List.of(new Font(0, columns), new Font(1, narrowColumns)), 180, true,
        List.of(PC437));
  }

  private static CodePage page(int id, String charset) {
    return new CodePage(id, Charset.forName(charset));
  }

  private static RasterImage solid(int width, int height, int argb) {
    int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return new RasterImage(width, height, pixels);
  }

  private static byte[] size(int width, int height) {
    return new byte[] { GS, 0x21, (byte) (((width - 1) << 4) | (height - 1)) };
  }

  private static byte[] feed(int lines) {
    return new byte[] { ESC, 0x64, (byte) lines };
  }

  /** Concatenates {@code byte[]} chunks and ASCII strings into one array. */
  private static byte[] cat(Object... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Object part : parts) {
      if (part instanceof byte[] bytes) {
        out.writeBytes(bytes);
      } else if (part instanceof String text) {
        out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
      } else {
        throw new IllegalArgumentException("Unsupported part: " + part);
      }
    }
    return out.toByteArray();
  }

  private static void assertBytes(byte[] expected, byte[] actual) {
    if (!Arrays.equals(expected, actual)) {
      fail("ESC/POS mismatch at byte " + firstDifference(expected, actual)
          + "\n  expected: " + hex(expected)
          + "\n  actual:   " + hex(actual));
    }
  }

  private static int firstDifference(byte[] a, byte[] b) {
    int shared = Math.min(a.length, b.length);
    for (int i = 0; i < shared; i++) {
      if (a[i] != b[i]) {
        return i;
      }
    }
    return a.length == b.length ? -1 : shared;
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString().trim();
  }
}
