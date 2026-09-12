//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

class ZplRendererTest {

  // dotsPerLine 800 / 40 columns -> a clean 20-dot char cell.
  private static final PrinterProfile PROFILE = PrinterProfile.of("zpl-test", 800, List.of(new Font(0, 40)), 203, false,
      List.of(new CodePage(0, Charset.forName("IBM437"))), PrinterLanguage.ZPL);

  // Setup for a 400 x 200 canvas: PW clamps to min(400, 800) = 400; the profile's
  // media defaults (direct thermal, gap) drive ^MTD and ^MNY.
  private static final String HEAD = "^XA^MUd^CI28^MTD^MNY^LH0,0^PW400^LL200";
  private static final String TAIL = "^XZ";

  private static final ComputedStyle PLAIN = ComputedStyle.INITIAL;
  private static final ComputedStyle BOLD = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

  @Test
  void rejectsReceiptFlowBlocks() {
    // A top-level Paragraph is receipt flow; the label backend has no mapping.
    final Paragraph flow = new Paragraph(List.of(new TextRun("x", PLAIN)), Alignment.LEFT);
    assertThrows(UnsupportedBlockException.class, () -> new ZplRenderer(PROFILE).render(List.of(flow)));
  }

  @Test
  void emptyJobIsEmpty() {
    assertEquals(0, new ZplRenderer(PROFILE).render(List.of()).length);
  }

  @Test
  void singleParagraphPlacement() {
    final Canvas canvas = Canvas.of(400, 200).place(50, 30, paragraph("HI")).build();
    assertEquals(HEAD + "^FO50,30^A0N,40,20^FDHI^FS" + TAIL, zpl(canvas));
  }

  @Test
  void mixedStyleParagraphBecomesOneFieldPerRun() {
    final Paragraph mixed = new Paragraph(List.of(new TextRun("AB", PLAIN), new TextRun("CD", BOLD)), Alignment.LEFT);
    final Canvas canvas = Canvas.of(400, 200).place(0, 0, mixed).build();
    // Two positioned fields; the second starts after the first's width (2 chars x 20 dots).
    assertEquals(HEAD + "^FO0,0^A0N,40,20^FDAB^FS^FO40,0^A0N,40,20^FDCD^FS" + TAIL, zpl(canvas));
  }

  @Test
  void barcodePlacementIsNative() {
    final Barcode barcode = new Barcode("12345", "code128", Alignment.LEFT);
    final Canvas canvas = Canvas.of(400, 200).place(10, 20, barcode).build();
    assertEquals(HEAD + "^FO10,20^BY2^BCN,100,Y,N,N,A^FD12345^FS" + TAIL, zpl(canvas));
  }

  @Test
  void imagePlacementIsGraphicField() {
    final ImageBlock image = new ImageBlock(Image.of(solidBlack(8, 1)), Alignment.LEFT);
    final Canvas canvas = Canvas.of(400, 200).place(0, 0, image).build();
    assertEquals(HEAD + "^FO0,0^GFA,1,1,1,FF" + TAIL, zpl(canvas));
  }

  @Test
  void wholeCanvasRotate180TransformsPositionAndOrientation() {
    final Canvas canvas = Canvas.of(400, 200).direction(Canvas.Direction.ROTATE_180).place(50, 30, paragraph("HI"))
        .build();
    // (50,30) -> (400-50, 200-30) = (350,170); orientation I.
    assertEquals(HEAD + "^FO350,170^A0I,40,20^FDHI^FS" + TAIL, zpl(canvas));
  }

  @Test
  void wholeCanvasRotate90CwTransformsPositionAndOrientation() {
    final Canvas canvas = Canvas.of(400, 200).direction(Canvas.Direction.ROTATE_90_CW).place(50, 30, paragraph("HI"))
        .build();
    // (50,30) -> (30, 400-50) = (30,350); orientation R.
    assertEquals(HEAD + "^FO30,350^A0R,40,20^FDHI^FS" + TAIL, zpl(canvas));
  }

  @Test
  void perPlacementRotationOverridesCanvasDirection() {
    final Canvas canvas = Canvas.of(400, 200).place(50, 30, paragraph("HI"), Canvas.Direction.ROTATE_180).build();
    assertEquals(HEAD + "^FO350,170^A0I,40,20^FDHI^FS" + TAIL, zpl(canvas));
  }

  @Test
  void borderOnAParagraphIsAGraphicBox() {
    final Paragraph bordered = new Paragraph(List.of(new TextRun("HI", PLAIN)), Alignment.LEFT,
        Border.all(Border.Style.SINGLE));
    final Canvas canvas = Canvas.of(400, 200).place(10, 10, bordered).build();
    // Box sized to the text extent (2 chars x 20 = 40 wide, one 40-dot line), then the text.
    assertEquals(HEAD + "^FO10,10^GB40,40,2,B,0^FO10,10^A0N,40,20^FDHI^FS" + TAIL, zpl(canvas));
  }

  @Test
  void doubleBorderDrawsTwoConcentricBoxes() {
    final Paragraph bordered = new Paragraph(List.of(new TextRun("HI", PLAIN)), Alignment.LEFT,
        Border.all(Border.Style.DOUBLE));
    final Canvas canvas = Canvas.of(400, 200).place(10, 10, bordered).build();
    final String out = zpl(canvas);
    assertTrue(out.contains("^FO10,10^GB40,40,2,B,0"), out); // outer
    assertTrue(out.contains("^FO14,14^GB32,32,2,B,0"), out); // inner, inset by 4
  }

  @Test
  void eachCanvasBecomesItsOwnLabel() {
    final Canvas a = Canvas.of(400, 200).place(0, 0, paragraph("A")).build();
    final Canvas b = Canvas.of(400, 200).place(0, 0, paragraph("B")).build();
    final String out = new String(new ZplRenderer(PROFILE).render(List.of(a, b)), StandardCharsets.UTF_8);
    assertEquals(2, count(out, "^XA"));
    assertEquals(2, count(out, "^XZ"));
  }

  private static Paragraph paragraph(String text) {
    return new Paragraph(List.of(new TextRun(text, PLAIN)), Alignment.LEFT);
  }

  private static String zpl(Canvas canvas) {
    return new String(new ZplRenderer(PROFILE).render(List.of(canvas)), StandardCharsets.UTF_8);
  }

  private static RasterImage solidBlack(int width, int height) {
    final int[] pixels = new int[width * height];
    Arrays.fill(pixels, 0xFF000000);
    return new RasterImage(width, height, pixels);
  }

  private static int count(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
      n++;
    }
    return n;
  }
}
