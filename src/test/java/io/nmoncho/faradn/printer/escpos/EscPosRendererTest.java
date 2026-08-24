package io.nmoncho.faradn.printer.escpos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.Document;
import io.nmoncho.faradn.Image;
import io.nmoncho.faradn.UnsupportedBlockException;
import io.nmoncho.faradn.document.Barcode;
import io.nmoncho.faradn.document.Block;
import io.nmoncho.faradn.document.ComputedStyle;
import io.nmoncho.faradn.document.ComputedStyle.Alignment;
import io.nmoncho.faradn.document.Cut;
import io.nmoncho.faradn.document.Feed;
import io.nmoncho.faradn.document.ImageBlock;
import io.nmoncho.faradn.document.Paragraph;
import io.nmoncho.faradn.document.Rule;
import io.nmoncho.faradn.document.TextRun;
import io.nmoncho.faradn.printer.TmT88vProfile;

/**
 * Golden-byte tests: assert the exact ESC/POS a document renders to. Expected
 * sequences are built from literal command bytes (independent of the command
 * layer's own constants) so a wrong constant is actually caught.
 */
public class EscPosRendererTest {

  private static final byte ESC = 0x1B;
  private static final byte GS = 0x1D;

  private static final byte[] INIT = { ESC, 0x40 };
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

  private final EscPosRenderer renderer = new EscPosRenderer(TmT88vProfile.INSTANCE);

  @Test
  void plainParagraph() {
    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("Hello", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(INIT, "Hello", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void boldRunOnlyTogglesAroundTheBoldRun() {
    ComputedStyle bold = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

    byte[] out = renderer.render(List.of(new Paragraph(List.of(
        new TextRun("Total:", bold),
        new TextRun(" 10", ComputedStyle.INITIAL)), Alignment.LEFT)));

    assertBytes(cat(INIT, BOLD_ON, "Total:", BOLD_OFF, " 10", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void headingEmitsAlignmentBoldAndSize() {
    ComputedStyle h1 = new ComputedStyle(true, false, 2, 2, Alignment.CENTER, false);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("Receipt", h1)), Alignment.CENTER)));

    assertBytes(cat(INIT, ALIGN_CENTER, BOLD_ON, size(2, 2), "Receipt",
        BOLD_OFF, size(1, 1), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void underlineTogglesAroundTheRun() {
    ComputedStyle underline = new ComputedStyle(false, true, 1, 1, Alignment.LEFT, false);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", underline)), Alignment.LEFT)));

    assertBytes(cat(INIT, UNDERLINE_ON, "x", UNDERLINE_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void invertTogglesAroundTheRun() {
    ComputedStyle invert = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, true);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", invert)), Alignment.LEFT)));

    assertBytes(cat(INIT, INVERT_ON, "x", INVERT_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void rightAlignmentEmitsEscA() {
    ComputedStyle right = new ComputedStyle(false, false, 1, 1, Alignment.RIGHT, false);

    byte[] out = renderer.render(List.of(
        new Paragraph(List.of(new TextRun("x", right)), Alignment.RIGHT)));

    assertBytes(cat(INIT, ALIGN_RIGHT, "x", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void ruleFillsTheLineWidth() {
    byte[] out = renderer.render(List.of(new Rule()));

    assertBytes(cat(INIT, "-".repeat(TmT88vProfile.INSTANCE.columns()), LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void feedBlockEmitsPrintAndFeedLines() {
    byte[] out = renderer.render(List.of(new Feed(3)));

    assertBytes(cat(INIT, feed(3), FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void fullCutBlock() {
    byte[] out = renderer.render(List.of(new Cut(false)));

    assertBytes(cat(INIT, FULL_CUT, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void partialCutBlock() {
    byte[] out = renderer.render(List.of(new Cut(true)));

    assertBytes(cat(INIT, PARTIAL_CUT, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void emptyDocumentStillFramesTheJob() {
    byte[] out = renderer.render(List.of());

    assertBytes(cat(INIT, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void endToEndBoldFromHtml() {
    byte[] out = renderer.render(Document.from("<p>a<b>b</b></p>").blocks());

    assertBytes(cat(INIT, "a", BOLD_ON, "b", BOLD_OFF, LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void endToEndCenterFromHtml() {
    byte[] out = renderer.render(Document.from("<center>Hi</center>").blocks());

    assertBytes(cat(INIT, ALIGN_CENTER, "Hi", LF, FEED_4, PARTIAL_CUT), out);
  }

  @Test
  void textReceiptFixtureRendersFramedJob() {
    byte[] out = renderer.render(
        Document.from(new File("src/test/resources/printjobs/receipt-text.html")).blocks());

    assertArrayEquals(INIT, Arrays.copyOfRange(out, 0, INIT.length));
    assertArrayEquals(PARTIAL_CUT, Arrays.copyOfRange(out, out.length - PARTIAL_CUT.length, out.length));
  }

  @Test
  void barcodeIsNotSupportedYet() {
    List<Block> blocks = List.of(new Barcode("12345678", "code128", Alignment.LEFT));

    assertThrows(UnsupportedBlockException.class, () -> renderer.render(blocks));
  }

  @Test
  void imageIsNotSupportedYet() {
    List<Block> blocks = List.of(new ImageBlock(Image.fromBase64("Zm9v"), Alignment.LEFT));

    assertThrows(UnsupportedBlockException.class, () -> renderer.render(blocks));
  }

  @Test
  void nullProfileIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new EscPosRenderer(null));
  }

  // ----- helpers -----

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
