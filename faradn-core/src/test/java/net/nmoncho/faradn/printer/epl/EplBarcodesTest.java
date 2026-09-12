//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.BarcodeException;
import net.nmoncho.faradn.document.BarcodeOptions;
import net.nmoncho.faradn.document.BarcodeOptions.Hri;
import net.nmoncho.faradn.document.BarcodeOptions.QrEc;

class EplBarcodesTest {

  @Test
  void code128Defaults() {
    // DEFAULT: height 100, module 0 (-> narrow 2, wide 6), HRI BELOW (-> B).
    assertEquals("B10,20,0,1,2,6,100,B,\"12345\"", EplBarcodes.encode(10, 20, 0, "code128", "12345",
        BarcodeOptions.DEFAULT));
  }

  @Test
  void oneDimensionalTypeCodes() {
    assertEquals("B0,0,0,3,2,6,100,B,\"CODE39\"", EplBarcodes.encode(0, 0, 0, "code39", "CODE39",
        BarcodeOptions.DEFAULT));
    assertEquals("B0,0,0,E30,2,6,100,B,\"123456789012\"", EplBarcodes.encode(0, 0, 0, "ean13", "123456789012",
        BarcodeOptions.DEFAULT));
    assertEquals("B0,0,0,K,2,6,100,B,\"A123B\"", EplBarcodes.encode(0, 0, 0, "codabar", "A123B",
        BarcodeOptions.DEFAULT));
  }

  @Test
  void hriOnlyToggles() {
    assertEquals("B0,0,0,1,2,6,100,N,\"X\"", EplBarcodes.encode(0, 0, 0, "code128", "X", opts(Hri.NONE)));
    assertEquals("B0,0,0,1,2,6,100,B,\"X\"", EplBarcodes.encode(0, 0, 0, "code128", "X", opts(Hri.ABOVE)));
  }

  @Test
  void rotationParameterIsPassedThrough() {
    assertEquals("B0,0,1,1,2,6,100,B,\"X\"", EplBarcodes.encode(0, 0, 1, "code128", "X", BarcodeOptions.DEFAULT));
  }

  @Test
  void pdf417And2DQr() {
    assertEquals("b5,5,P,\"DATA\"", EplBarcodes.encode(5, 5, 0, "pdf417", "DATA", BarcodeOptions.DEFAULT));
    // QR is emitted (b,Q) though it is Japanese-models only; the renderer warns.
    assertEquals("b5,5,Q,\"HELLO\"", EplBarcodes.encode(5, 5, 0, "qr", "HELLO", BarcodeOptions.DEFAULT));
  }

  @Test
  void classification() {
    assertTrue(EplBarcodes.isTwoDimensional("qr"));
    assertTrue(EplBarcodes.isTwoDimensional("pdf417"));
    assertFalse(EplBarcodes.isTwoDimensional("code128"));
    assertTrue(EplBarcodes.isQrCode("qr"));
    assertFalse(EplBarcodes.isQrCode("pdf417"));
  }

  @Test
  void validation() {
    assertThrows(BarcodeException.class, () -> EplBarcodes.encode(0, 0, 0, "bogus", "x", BarcodeOptions.DEFAULT));
    assertThrows(BarcodeException.class, () -> EplBarcodes.encode(0, 0, 0, "ean13", "12345", BarcodeOptions.DEFAULT));
  }

  private static BarcodeOptions opts(Hri hri) {
    return new BarcodeOptions(100, 0, hri, QrEc.M);
  }
}
