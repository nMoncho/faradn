//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import net.nmoncho.faradn.UnsupportedBlockException;
import net.nmoncho.faradn.document.Barcode;
import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.Cell;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Cut;
import net.nmoncho.faradn.document.Drawer;
import net.nmoncho.faradn.document.Feed;
import net.nmoncho.faradn.document.ImageBlock;
import net.nmoncho.faradn.document.LineHeight;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.Rule;
import net.nmoncho.faradn.document.Table;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.CodePage;
import net.nmoncho.faradn.printer.Font;
import net.nmoncho.faradn.printer.PrinterLanguage;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.StarProfiles;
import net.nmoncho.faradn.printer.starprnt.commands.StarBarcodeCommands;

/**
 * Golden-byte tests for the StarPRNT renderer: assert the exact bytes a
 * document
 * renders to. Expected sequences are built from literal Star opcodes (not the
 * command layer's own constants), so a wrong constant is actually caught.
 */
class StarPrntRendererTest {

  private static final byte ESC = 0x1B;
  private static final byte GS = 0x1D;

  private static final byte[] INIT = { ESC, 0x40 };
  private static final byte[] SELECT_PAGE_0 = { ESC, GS, 0x74, 0x00 };
  private static final byte[] HEAD = cat(INIT, SELECT_PAGE_0);
  private static final byte[] LF = { 0x0A };
  private static final byte[] BOLD_ON = { ESC, 0x45 };
  private static final byte[] BOLD_OFF = { ESC, 0x46 };
  private static final byte[] UNDERLINE_ON = { ESC, 0x2D, 0x01 };
  private static final byte[] UNDERLINE_OFF = { ESC, 0x2D, 0x00 };
  private static final byte[] INVERT_ON = { ESC, 0x34 };
  private static final byte[] INVERT_OFF = { ESC, 0x35 };
  private static final byte[] ALIGN_CENTER = { ESC, GS, 0x61, 0x01 };
  private static final byte[] ALIGN_RIGHT = { ESC, GS, 0x61, 0x02 };
  private static final byte[] FEED_4 = { ESC, 0x61, 0x04 };
  private static final byte[] PARTIAL_CUT = { ESC, 0x64, 0x01 };
  private static final byte[] FULL_CUT = { ESC, 0x64, 0x00 };
  private static final byte[] DRAWER_1 = { ESC, 0x07, 0x05, 0x32, 0x07 };
  private static final byte[] DRAWER_2 = { ESC, 0x07, 0x05, 0x32, 0x1A };
  private static final byte[] FONT_B = { ESC, 0x1E, 0x46, 0x01 };
  private static final byte[] FONT_A = { ESC, 0x1E, 0x46, 0x00 };

  private static final CodePage PC437 = new CodePage(0, Charset.forName("IBM437"));

  private final StarPrntRenderer star = new StarPrntRenderer(starProfile(48, PC437));

  @Test
  void plainParagraph() {
    byte[] out = star
        .render(List.of(new Paragraph(List.of(new TextRun("Hello", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "Hello", LF, FEED_4, PARTIAL_CUT), out);
  }

  // --- Kanji (multi-byte) text -------------------------------------------------
  // StarPRNT toggles Shift-JIS Kanji mode with ESC $ 1 / ESC $ 0 (no separate
  // code-system select), and encodes CJK glyphs as Shift-JIS.
  private static final byte[] KANJI_ON = { ESC, 0x24, 0x01 }; // ESC $ 1
  private static final byte[] KANJI_OFF = { ESC, 0x24, 0x00 }; // ESC $ 0
  private static final byte[] SJIS_KANJI = { (byte) 0x8A, (byte) 0xBF }; // 漢 in Shift-JIS
  private static final byte[] SJIS_JI = { (byte) 0x8E, (byte) 0x9A }; // 字 in Shift-JIS

  private final StarPrntRenderer starKanji = new StarPrntRenderer(
      PrinterProfile.of("star-jp", 576, List.of(new Font(0, 48)), 203, true, List.of(PC437),
          PrinterLanguage.STAR_PRNT, Charset.forName("windows-31j")));

  @Test
  void kanjiParagraphBracketsCjkWithShiftJisMode() {
    byte[] out = starKanji.render(List.of(
        new Paragraph(List.of(new TextRun("A漢B", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "A", KANJI_ON, SJIS_KANJI, KANJI_OFF, "B", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void kanjiOnlyRunClosesModeAtEnd() {
    byte[] out = starKanji.render(List.of(
        new Paragraph(List.of(new TextRun("漢字", ComputedStyle.INITIAL)), Alignment.LEFT)));

    // ESC $ 1, both glyphs, the paragraph LF, then finish() emits ESC $ 0.
    assertBytes(cat(HEAD, KANJI_ON, SJIS_KANJI, SJIS_JI, LF, KANJI_OFF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void cjkWithoutAKanjiRomFallsBackToReplacement() {
    // The default star profile has no kanjiCharset: 漢 maps to '?'.
    byte[] out = star.render(List.of(
        new Paragraph(List.of(new TextRun("漢", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "?", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void boldIsTwoOpcodesAroundTheRun() {
    ComputedStyle bold = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

    byte[] out = star.render(List.of(new Paragraph(List.of(
        new TextRun("Total:", bold),
        new TextRun(" 10", ComputedStyle.INITIAL)), Alignment.LEFT)));

    // ESC E / ESC F, no parameter byte.
    assertBytes(cat(HEAD, BOLD_ON, "Total:", BOLD_OFF, " 10", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void headingEmitsAlignmentBoldAndSize() {
    ComputedStyle h1 = new ComputedStyle(true, false, 2, 2, Alignment.CENTER, false);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("Receipt", h1)), Alignment.CENTER)));

    // ESC GS a 1, ESC E, ESC i n1=height-1 n2=width-1, then reset.
    assertBytes(cat(HEAD, ALIGN_CENTER, BOLD_ON, size(2, 2), "Receipt", BOLD_OFF, size(1, 1), LF,
        FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void underlineTogglesAroundTheRun() {
    ComputedStyle underline = new ComputedStyle(false, true, 1, 1, Alignment.LEFT, false);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", underline)), Alignment.LEFT)));

    assertBytes(cat(HEAD, UNDERLINE_ON, "x", UNDERLINE_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void italicIsANoOp() {
    // StarPRNT has no italic opcode, so an italic run emits only its text.
    ComputedStyle italic = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, true);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", italic)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void upsideDownTogglesWithSiAndDc2() {
    // upsideDown = ComputedStyle field 10; StarPRNT uses SI (0x0F) / DC2 (0x12).
    ComputedStyle ud = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false, false, true, false);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", ud)), Alignment.LEFT)));

    assertBytes(cat(HEAD, new byte[] { 0x0F }, "x", new byte[] { 0x12 }, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void doubleStrikeAndSmoothingAreNoOps() {
    // StarPRNT has no double-strike/smoothing command, so both emit only the text.
    ComputedStyle fx = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false, true, false, true);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", fx)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void invertTogglesAroundTheRunWithEsc4And5() {
    ComputedStyle invert = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, true);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", invert)), Alignment.LEFT)));

    assertBytes(cat(HEAD, INVERT_ON, "x", INVERT_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void sizeClampsToSixTimes() {
    // ComputedStyle allows up to 8x; the TSP143IV's ESC i tops out at 6x, so it clamps.
    ComputedStyle huge = new ComputedStyle(false, false, 8, 8, Alignment.LEFT, false);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", huge)), Alignment.LEFT)));

    assertBytes(cat(HEAD, size(6, 6), "x", size(1, 1), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void rightAlignmentEmitsEscGsA() {
    ComputedStyle right = new ComputedStyle(false, false, 1, 1, Alignment.RIGHT, false);

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", right)), Alignment.RIGHT)));

    assertBytes(cat(HEAD, ALIGN_RIGHT, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void feedEmitsEscA() {
    byte[] out = star.render(List.of(new Feed(3)));

    assertBytes(cat(HEAD, feedLines(3), FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void cutFullAndPartial() {
    assertBytes(cat(HEAD, FULL_CUT), star.render(List.of(new Cut(false))));
    assertBytes(cat(HEAD, PARTIAL_CUT), star.render(List.of(new Cut(true))));
  }

  @Test
  void cashDrawerPins() {
    assertBytes(cat(HEAD, DRAWER_1, FEED_4, PARTIAL_CUT), star.render(List.of(new Drawer(2))));
    assertBytes(cat(HEAD, DRAWER_2, FEED_4, PARTIAL_CUT), star.render(List.of(new Drawer(5))));
  }

  @Test
  void ruleEmitsDashes() {
    byte[] out = new StarPrntRenderer(starProfile(5, PC437)).render(List.of(new Rule()));

    assertBytes(cat(HEAD, "-----", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void codePageSelectedAtHeadWithEscGsT() {
    // A non-zero default page id is selected up front (ESC GS t n).
    CodePage cp1252 = new CodePage(32, Charset.forName("windows-1252"));
    byte[] out = new StarPrntRenderer(starProfile(48, cp1252))
        .render(List.of(new Paragraph(List.of(new TextRun("x", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(INIT, new byte[] { ESC, GS, 0x74, 32 }, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void tableLaysOutOnACharacterGrid() {
    ComputedStyle plain = ComputedStyle.INITIAL;
    Cell left = new Cell(List.of(new TextRun("ab", plain)), Alignment.LEFT);
    Cell right = new Cell(List.of(new TextRun("cd", plain)), Alignment.RIGHT);
    Table table = new Table(List.of(List.of(left, right)));

    byte[] out = new StarPrntRenderer(starProfile(9, PC437)).render(List.of(table));

    // columnWidth = (9 - 1) / 2 = 4: "ab" + 2 pad, gutter, 2 pad + "cd"
    assertBytes(cat(HEAD, "ab", "  ", " ", "  ", "cd", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fontSelectUsesEscRsF() {
    // A profile with Font A and Font B; a font-b run selects it via ESC RS F 1.
    PrinterProfile twoFonts = PrinterProfile.of("star2", 576, List.of(new Font(0, 48), new Font(1, 64)), 203, true,
        List.of(PC437), PrinterLanguage.STAR_PRNT);
    ComputedStyle fontB = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 1);

    byte[] out = new StarPrntRenderer(twoFonts)
        .render(List.of(new Paragraph(List.of(new TextRun("x", fontB)), Alignment.LEFT)));

    assertBytes(cat(HEAD, FONT_B, "x", FONT_A, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void imageWrapsTheSharedRasterInEscGsS() {
    RasterImage img = solid(16, 4, 0xFF000000);
    byte[] out = star.render(List.of(new ImageBlock(Image.of(img), Alignment.LEFT)));

    // renderImage clears style, aligns left (both no-ops from INITIAL), then the ESC GS S raster.
    assertBytes(cat(HEAD, StarRasterizer.raster(img, 576), FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void barcodeDelegatesToStarBarcodeCommands() {
    Barcode barcode = new Barcode("12345678", "code128", Alignment.LEFT);
    byte[] out = star.render(List.of(barcode));

    assertBytes(cat(HEAD, StarBarcodeCommands.encode("code128", "12345678"), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void qrDelegatesToStarBarcodeCommands() {
    // 2D goes through the Star encoder (ESC GS y), not the ESC/POS one.
    Barcode qr = new Barcode("HELLO", "qr", Alignment.LEFT);
    byte[] out = star.render(List.of(qr));

    assertBytes(cat(HEAD, StarBarcodeCommands.encode("qr", "HELLO"), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void codePageSwitchesMidTextWithEscGsT() {
    // '€' is not in CP437 but is in CP1252 (0x80), forcing an inline switch to
    // page 32 via the Star selector (ESC GS t 32), not ESC/POS's ESC t.
    CodePage cp1252 = new CodePage(32, Charset.forName("windows-1252"));
    PrinterProfile p = PrinterProfile.of("star", 576, List.of(new Font(0, 48)), 203, true,
        List.of(PC437, cp1252), PrinterLanguage.STAR_PRNT);

    byte[] out = new StarPrntRenderer(p)
        .render(List.of(new Paragraph(List.of(new TextRun("€", ComputedStyle.INITIAL)), Alignment.LEFT)));

    // HEAD selects CP437 (id 0, the default); then the inline switch to page 32.
    assertBytes(cat(HEAD, new byte[] { ESC, GS, 0x74, 32 }, new byte[] { (byte) 0x80 }, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void receiptTextFixtureIsFramedWithStarInitAndCut() {
    // Anchor test: the whole receipt fixture renders through the Star backend;
    // assert only the ESC @ + ESC GS t head and the ESC d cut tail.
    byte[] out = new StarPrntRenderer(StarProfiles.tsp143iv())
        .render(Document.from(new File("src/test/resources/printjobs/receipt-text.html")).blocks());

    assertArrayEquals(new byte[] { ESC, 0x40, ESC, GS, 0x74, 0x01 }, Arrays.copyOfRange(out, 0, 6)); // ESC @ + ESC GS t 1
    assertArrayEquals(PARTIAL_CUT, Arrays.copyOfRange(out, out.length - PARTIAL_CUT.length, out.length)); // ESC d 1
  }

  @Test
  void looseLineHeightPadsEachLineWithAnExtraDotFeed() {
    // StarPRNT has no set-line-spacing command, so a looser line-height is
    // emulated with a one-time ESC I feed after each line. Font A cell = 24 dots,
    // default pitch = 24 dots; line-height 2.0 -> 48 dots -> ESC I 24 extra.
    ComputedStyle loose = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false, LineHeight.parse("2"));

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", loose)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "x", LF, new byte[] { ESC, 0x49, 24 }, FEED_4, PARTIAL_CUT), out); // ESC I 24
  }

  @Test
  void fixedLineHeightPadsToTheDotTarget() {
    // line-height: 40px -> 40 dots; default pitch 24 -> ESC I 16 extra.
    ComputedStyle fixed = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false,
        LineHeight.parse("40px"));

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", fixed)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "x", LF, new byte[] { ESC, 0x49, 16 }, FEED_4, PARTIAL_CUT), out); // ESC I 16
  }

  @Test
  void tightLineHeightEmitsNoExtraFeed() {
    // line-height 1.0 = 24 dots = the default pitch; feeds only loosen, so a
    // line-height at or below the default clamps to it (no ESC I).
    ComputedStyle tight = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false, 0, false, LineHeight.parse("1"));

    byte[] out = star.render(List.of(new Paragraph(List.of(new TextRun("x", tight)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void pageModeCanvasIsUnsupported() {
    Canvas canvas = Canvas.of(512, 160)
        .place(0, 0, new Paragraph(List.of(new TextRun("x", ComputedStyle.INITIAL)), Alignment.LEFT))
        .build();

    assertThrows(UnsupportedBlockException.class, () -> star.render(List.of(canvas)));
  }

  // ----- helpers (mirror EscPosRendererTest) -----

  private static PrinterProfile starProfile(int columns, CodePage codePage) {
    return PrinterProfile.of("star", 576, List.of(new Font(0, columns)), 203, true, List.of(codePage),
        PrinterLanguage.STAR_PRNT);
  }

  private static RasterImage solid(int width, int height, int argb) {
    int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return new RasterImage(width, height, pixels);
  }

  private static byte[] size(int width, int height) {
    return new byte[] { ESC, 0x69, (byte) (height - 1), (byte) (width - 1) }; // ESC i n1=height, n2=width
  }

  private static byte[] feedLines(int lines) {
    return new byte[] { ESC, 0x61, (byte) lines };
  }

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
      fail("StarPRNT mismatch at byte " + firstDifference(expected, actual)
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
