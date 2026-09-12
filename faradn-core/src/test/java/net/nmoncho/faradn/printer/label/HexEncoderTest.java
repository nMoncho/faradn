//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.label;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HexEncoderTest {

  @Test
  void encodesEachByteAsTwoUppercaseHexDigits() {
    assertEquals("000FFF1D80", HexEncoder.toHex(new byte[] { 0x00, 0x0F, (byte) 0xFF, 0x1D, (byte) 0x80 }));
  }

  @Test
  void encodesEmptyToEmpty() {
    assertEquals("", HexEncoder.toHex(new byte[0]));
  }

  @Test
  void rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> HexEncoder.toHex(null));
  }
}
