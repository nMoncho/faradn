//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.BarcodeException;
import net.nmoncho.faradn.document.BarcodeOptions;
import net.nmoncho.faradn.document.BarcodeOptions.Hri;
import net.nmoncho.faradn.document.BarcodeOptions.QrEc;
import net.nmoncho.faradn.document.Canvas;

class ZplBarcodesTest {

  @Test
  void code128DefaultsToModule2Height100HriBelow() {
    // BarcodeOptions.DEFAULT = height 100, module 0 (-> 2), HRI BELOW, QR M.
    assertEquals("^BY2^BCN,100,Y,N,N,A^FDABC123^FS", ZplBarcodes.encode("code128", "ABC123"));
  }

  @Test
  void code39() {
    assertEquals("^BY2^B3N,N,100,Y,N^FDCODE39^FS", ZplBarcodes.encode("code39", "CODE39"));
  }

  @Test
  void ean13ValidatesDigitCount() {
    assertEquals("^BY2^BEN,100,Y,N^FD123456789012^FS", ZplBarcodes.encode("ean13", "123456789012"));
    assertThrows(BarcodeException.class, () -> ZplBarcodes.encode("ean13", "12345"));
  }

  @Test
  void hriMapsToInterpretationLineFlags() {
    // NONE -> no line; ABOVE -> line + above; BOTH -> degrades to below (line, not above).
    assertEquals("^BY2^BCN,100,N,N,N,A^FDX^FS", ZplBarcodes.encode("code128", "X", opts(Hri.NONE)));
    assertEquals("^BY2^BCN,100,Y,Y,N,A^FDX^FS", ZplBarcodes.encode("code128", "X", opts(Hri.ABOVE)));
    assertEquals("^BY2^BCN,100,Y,N,N,A^FDX^FS", ZplBarcodes.encode("code128", "X", opts(Hri.BOTH)));
  }

  @Test
  void qrCarriesEccAndInputModeInFieldData() {
    // Corrected form: ^FD<ec><A|M>,data ; second char is input mode (A), NOT a mask.
    assertEquals("^BQN,2,2^FDMA,HELLO^FS", ZplBarcodes.encode("qr", "HELLO"));
    final BarcodeOptions highEc = new BarcodeOptions(100, 5, Hri.BELOW, QrEc.H);
    assertEquals("^BQN,2,5^FDHA,HELLO^FS", ZplBarcodes.encode("qr", "HELLO", highEc));
  }

  @Test
  void pdf417UsesDefaults() {
    assertEquals("^BY2^B7N^FDDATA^FS", ZplBarcodes.encode("pdf417", "DATA"));
  }

  @Test
  void rotatedBarcodeSetsFieldOrientation() {
    assertEquals("^BY2^BCR,100,Y,N,N,A^FDAB^FS",
        ZplBarcodes.encode("code128", "AB", BarcodeOptions.DEFAULT, Canvas.Direction.ROTATE_90_CW));
  }

  @Test
  void isTwoDimensional() {
    assertTrue(ZplBarcodes.isTwoDimensional("qr"));
    assertTrue(ZplBarcodes.isTwoDimensional("pdf417"));
    assertFalse(ZplBarcodes.isTwoDimensional("code128"));
  }

  @Test
  void unknownSymbologyThrows() {
    assertThrows(BarcodeException.class, () -> ZplBarcodes.encode("bogus", "x"));
  }

  private static BarcodeOptions opts(Hri hri) {
    return new BarcodeOptions(100, 0, hri, QrEc.M);
  }
}
