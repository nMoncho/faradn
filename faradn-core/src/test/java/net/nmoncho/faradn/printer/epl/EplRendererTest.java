//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.RasterImage;
import net.nmoncho.faradn.UnsupportedBlockException;
import net.nmoncho.faradn.document.Barcode;
import net.nmoncho.faradn.document.Border;
import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.ImageBlock;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.CodePage;
import net.nmoncho.faradn.printer.Font;
import net.nmoncho.faradn.printer.PrinterLanguage;
import net.nmoncho.faradn.printer.PrinterProfile;

class EplRendererTest {

  private static final Charset IBM437 = Charset.forName("IBM437");

  // dotsPerLine 720 / 60 columns -> a clean 12-dot char cell.
  private static final PrinterProfile PROFILE = PrinterProfile.of("epl-test", 720, List.of(new Font(0, 60)), 203, false,
      List.of(new CodePage(0, IBM437)), PrinterLanguage.EPL);

  // Setup for a 400 x 200 canvas: q clamps to min(400, 720) = 400; gap media -> gap 24.
  private static final String HEAD = "I8,0,001\r\nq400\r\nQ200,24\r\nN\r\n";
  private static final String TAIL = "P1\r\n";

  private static final ComputedStyle PLAIN = ComputedStyle.INITIAL;
  private static final ComputedStyle BOLD = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

  @Test
  void rejectsReceiptFlowBlocks() {
    final Paragraph flow = new Paragraph(List.of(new TextRun("x", PLAIN)), Alignment.LEFT);
    assertThrows(UnsupportedBlockException.class, () -> new EplRenderer(PROFILE).render(List.of(flow)));
  }

  @Test
  void emptyJobIsEmpty() {
    assertEquals(0, new EplRenderer(PROFILE).render(List.of()).length);
  }

  @Test
  void singleParagraphPlacement() {
    final Canvas canvas = Canvas.of(400, 200).place(50, 30, paragraph("HI")).build();
    assertEquals(HEAD + "A50,30,0,3,1,1,N,\"HI\"\r\n" + TAIL, epl(canvas));
  }

  @Test
  void mixedStyleParagraphBecomesOneFieldPerRun() {
    final Paragraph mixed = new Paragraph(List.of(new TextRun("AB", PLAIN), new TextRun("CD", BOLD)), Alignment.LEFT);
    final Canvas canvas = Canvas.of(400, 200).place(0, 0, mixed).build();
    // charWidth 12: the second run starts at 2 chars x 12 = 24 dots. Bold has no EPL effect.
    assertEquals(HEAD + "A0,0,0,3,1,1,N,\"AB\"\r\nA24,0,0,3,1,1,N,\"CD\"\r\n" + TAIL, epl(canvas));
  }

  @Test
  void barcodePlacementIsNative() {
    final Barcode barcode = new Barcode("12345", "code128", Alignment.LEFT);
    final Canvas canvas = Canvas.of(400, 200).place(10, 20, barcode).build();
    assertEquals(HEAD + "B10,20,0,1,2,6,100,B,\"12345\"\r\n" + TAIL, epl(canvas));
  }

  @Test
  void wholeCanvasRotate180TransformsPositionAndRotation() {
    final Canvas canvas = Canvas.of(400, 200).direction(Canvas.Direction.ROTATE_180).place(50, 30, paragraph("HI"))
        .build();
    // (50,30) -> (350,170); EPL rotation 2.
    assertEquals(HEAD + "A350,170,2,3,1,1,N,\"HI\"\r\n" + TAIL, epl(canvas));
  }

  @Test
  void borderOnAParagraphIsABox() {
    final Paragraph bordered = new Paragraph(List.of(new TextRun("HI", PLAIN)), Alignment.LEFT,
        Border.all(Border.Style.SINGLE));
    final Canvas canvas = Canvas.of(400, 200).place(10, 10, bordered).build();
    // Text extent 24 x 24: box corners (10,10)-(34,34), then the text.
    assertEquals(HEAD + "X10,10,2,34,34\r\nA10,10,0,3,1,1,N,\"HI\"\r\n" + TAIL, epl(canvas));
  }

  @Test
  void imagePlacementIsGraphicWriteWithInvertedBody() {
    final ImageBlock image = new ImageBlock(Image.of(solidBlack(8, 1)), Alignment.LEFT);
    final Canvas canvas = Canvas.of(400, 200).place(0, 0, image).build();
    // Solid black 8x1 -> body 0xFF in the bitmap -> inverted 0x00 for EPL GW.
    final byte[] expected = concat(HEAD.getBytes(IBM437), "GW0,0,1,1".getBytes(IBM437), new byte[] { 0x00, 0x0D, 0x0A },
        TAIL.getBytes(IBM437));
    assertArrayEquals(expected, new EplRenderer(PROFILE).render(List.of(canvas)));
  }

  @Test
  void eachCanvasBecomesItsOwnLabel() {
    final Canvas a = Canvas.of(400, 200).place(0, 0, paragraph("A")).build();
    final Canvas b = Canvas.of(400, 200).place(0, 0, paragraph("B")).build();
    final String out = new String(new EplRenderer(PROFILE).render(List.of(a, b)), IBM437);
    assertEquals(2, count(out, "I8,0,001"));
    assertEquals(2, count(out, "\r\nP1\r\n"));
  }

  private static Paragraph paragraph(String text) {
    return new Paragraph(List.of(new TextRun(text, PLAIN)), Alignment.LEFT);
  }

  private static String epl(Canvas canvas) {
    return new String(new EplRenderer(PROFILE).render(List.of(canvas)), IBM437);
  }

  private static RasterImage solidBlack(int width, int height) {
    final int[] pixels = new int[width * height];
    Arrays.fill(pixels, 0xFF000000);
    return new RasterImage(width, height, pixels);
  }

  private static byte[] concat(byte[]... parts) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }

  private static int count(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
      n++;
    }
    return n;
  }
}
