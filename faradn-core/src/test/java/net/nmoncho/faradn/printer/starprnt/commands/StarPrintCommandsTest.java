//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.starprnt.commands.StarPrintCommands.Lines;

class StarPrintCommandsTest {

  @Test
  void lineFeed() {
    assertArrayEquals(new byte[] { 0x0A }, StarPrintCommands.LINE_FEED.getCode());
  }

  @Test
  void feedLinesIsEscA() {
    // ESC a n (feed n lines) - not ESC/POS's ESC a (alignment).
    assertArrayEquals(new byte[] { 0x1B, 0x61, 0x03 }, StarPrintCommands.PRINT_AND_FEED_LINES.getCode(Lines.of(3)));
  }

  @Test
  void feedLinesRangeChecked() {
    assertThrows(IllegalArgumentException.class, () -> Lines.of(0));
    assertThrows(IllegalArgumentException.class, () -> Lines.of(128));
  }
}
