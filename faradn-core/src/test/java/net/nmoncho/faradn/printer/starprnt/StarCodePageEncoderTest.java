//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.CodePage;

class StarCodePageEncoderTest {

  @Test
  void selectPageIsEscGsTn() {
    // ESC GS t n : extra leading GS versus ESC/POS ESC t n.
    assertArrayEquals(new byte[] { 0x1B, 0x1D, 0x74, 0x00 }, StarCodePageEncoder.selectPage(0));
    assertArrayEquals(new byte[] { 0x1B, 0x1D, 0x74, 0x10 }, StarCodePageEncoder.selectPage(16));
  }

  @Test
  void asciiOnTheCurrentPageEmitsNoSwitch() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePage cp437 = new CodePage(1, Charset.forName("IBM437"));
    StarCodePageEncoder.of(out, cp437, List.of(cp437)).emit("Hi");

    assertArrayEquals("Hi".getBytes(StandardCharsets.US_ASCII), out.toByteArray());
  }

  @Test
  void switchesPagesWithEscGsT() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePage ascii = new CodePage(0, StandardCharsets.US_ASCII);
    final CodePage cp437 = new CodePage(5, Charset.forName("IBM437"));
    // 'é' is not in US-ASCII but is in CP437, forcing a switch to page 5.
    StarCodePageEncoder.of(out, ascii, List.of(cp437)).emit("é");

    final byte[] bytes = out.toByteArray();
    assertArrayEquals(new byte[] { 0x1B, 0x1D, 0x74, 0x05 }, Arrays.copyOfRange(bytes, 0, 4)); // ESC GS t 5
  }
}
