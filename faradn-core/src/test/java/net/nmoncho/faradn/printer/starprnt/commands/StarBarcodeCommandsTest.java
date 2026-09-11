//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.BarcodeException;
import net.nmoncho.faradn.document.BarcodeOptions;

class StarBarcodeCommandsTest {

  @Test
  void code128IsOneCombinedRsTerminatedCommandWithNoCodeSetPrefix() {
    byte[] out = StarBarcodeCommands.encode("code128", "ABC");

    // ESC b n1=6(Code128) n2=2(HRI below+LF) n3=2(width) n4=100(height) "ABC" RS
    // Star auto-selects the start code, so no {B prefix.
    assertArrayEquals(new byte[] { 0x1B, 0x62, 0x06, 0x02, 0x02, 0x64, 'A', 'B', 'C', 0x1E }, out);
  }

  @Test
  void hriNoneSelectsFlagOne() {
    byte[] out = StarBarcodeCommands.encode("code39", "ABC",
        new BarcodeOptions(100, 0, BarcodeOptions.Hri.NONE, BarcodeOptions.QrEc.M));
    // n1=4 (Code39), n2=1 (no HRI + LF)
    assertArrayEquals(new byte[] { 0x1B, 0x62, 0x04, 0x01, 0x02, 0x64, 'A', 'B', 'C', 0x1E }, out);
  }

  @Test
  void dataWithRsTerminatorIsRejected() {
    assertThrows(BarcodeException.class, () -> StarBarcodeCommands.encode("code39", "A\u001EB"));
  }

  @Test
  void ean13ValidatesItsData() {
    assertThrows(BarcodeException.class, () -> StarBarcodeCommands.encode("ean13", "12345"));
    StarBarcodeCommands.encode("ean13", "123456789012"); // 12 digits: valid
  }

  @Test
  void unknownSymbologyThrows() {
    assertThrows(BarcodeException.class, () -> StarBarcodeCommands.encode("bogus", "123"));
  }

  @Test
  void qrSetsModelThenPrintsViaEscGsY() {
    byte[] out = StarBarcodeCommands.encode("qr", "hi");

    byte[] head = { 0x1B, 0x1D, 0x79, 0x53, 0x30, 0x02 }; // ESC GS y S 0 : model 2
    assertArrayEquals(head, Arrays.copyOfRange(out, 0, head.length));

    byte[] tail = { 0x1B, 0x1D, 0x79, 0x50 }; // ESC GS y P : print
    assertArrayEquals(tail, Arrays.copyOfRange(out, out.length - tail.length, out.length));
  }

  @Test
  void pdf417SetsSizeThenPrintsViaEscGsX() {
    byte[] out = StarBarcodeCommands.encode("pdf417", "hi");

    byte[] head = { 0x1B, 0x1D, 0x78, 0x53, 0x30, 0x00, 0x00, 0x00 }; // ESC GS x S 0 : size (auto)
    assertArrayEquals(head, Arrays.copyOfRange(out, 0, head.length));

    byte[] tail = { 0x1B, 0x1D, 0x78, 0x50 }; // ESC GS x P : print
    assertArrayEquals(tail, Arrays.copyOfRange(out, out.length - tail.length, out.length));
  }

  @Test
  void twoDimensionalDetection() {
    assertTrue(StarBarcodeCommands.isTwoDimensional("qr"));
    assertTrue(StarBarcodeCommands.isTwoDimensional("pdf417"));
    assertFalse(StarBarcodeCommands.isTwoDimensional("code128"));
    assertFalse(StarBarcodeCommands.isTwoDimensional(null));
  }
}
