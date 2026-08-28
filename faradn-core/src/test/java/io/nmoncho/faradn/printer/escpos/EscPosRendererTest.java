package io.nmoncho.faradn.printer.escpos;

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

import io.nmoncho.faradn.Document;
import io.nmoncho.faradn.Image;
import io.nmoncho.faradn.RasterImage;
import io.nmoncho.faradn.document.Barcode;
import io.nmoncho.faradn.document.Cell;
import io.nmoncho.faradn.document.ComputedStyle;
import io.nmoncho.faradn.document.ComputedStyle.Alignment;
import io.nmoncho.faradn.document.Cut;
import io.nmoncho.faradn.document.Feed;
import io.nmoncho.faradn.document.ImageBlock;
import io.nmoncho.faradn.document.Paragraph;
import io.nmoncho.faradn.document.Rule;
import io.nmoncho.faradn.document.Table;
import io.nmoncho.faradn.document.TextRun;
import io.nmoncho.faradn.printer.CodePage;
import io.nmoncho.faradn.printer.PrinterProfile;
import io.nmoncho.faradn.printer.escpos.commands.BarcodeCommands;

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
  private static final byte[] INVERT_ON = { GS, 0x42, 0x01 };
  private static final byte[] INVERT_OFF = { GS, 0x42, 0x00 };
  private static final byte[] ALIGN_CENTER = { ESC, 0x61, 0x01 };
  private static final byte[] ALIGN_RIGHT = { ESC, 0x61, 0x02 };
  private static final byte[] FEED_4 = { ESC, 0x64, 0x04 };
  private static final byte[] PARTIAL_CUT = { GS, 0x56, 0x01 };
  private static final byte[] FULL_CUT = { GS, 0x56, 0x00 };

  private static final PrinterProfile TM_T88V = PrinterProfile.load("TM-T88V").orElseThrow();

  private static final CodePage PC437 = page(0, "IBM437");
  private static final CodePage PC858 = page(19, "IBM00858");
  private static final CodePage WPC1252 = page(16, "windows-1252");
  private static final CodePage PC866 = page(17, "IBM866");
  private static final CodePage PC852 = page(18, "IBM852");

  private final EscPosRenderer renderer = new EscPosRenderer(TM_T88V);
  // A profile with a small, known code-page set for deterministic switching tests.
  private final EscPosRenderer multiPage = new EscPosRenderer(
      PrinterProfile.of("Multi-page", 512, 42, 180, true, List.of(PC437, WPC1252, PC866, PC852)));

  @Test
  void plainParagraph() {
    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("Hello", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(HEAD, "Hello", LF, FEED_4, PARTIAL_CUT), out);
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
  void invertTogglesAroundTheRun() {
    ComputedStyle invert = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, true);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", invert)), Alignment.LEFT)));

    assertBytes(cat(HEAD, INVERT_ON, "x", INVERT_OFF, LF, FEED_4, PARTIAL_CUT), out);
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
  void emptyDocumentStillFramesTheJob() {
    byte[] out = renderer.render(List.of());

    assertBytes(cat(HEAD, FEED_4, PARTIAL_CUT), out);
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
  void nullProfileIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new EscPosRenderer(null));
  }

  // ----- helpers -----

  private static PrinterProfile profile(int columns, CodePage codePage) {
    return PrinterProfile.of("test", 512, columns, 180, true, List.of(codePage));
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
