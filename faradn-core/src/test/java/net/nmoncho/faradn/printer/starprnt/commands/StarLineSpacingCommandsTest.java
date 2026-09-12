//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.starprnt.commands.StarLineSpacingCommands.Amount;

class StarLineSpacingCommandsTest {

  @Test
  void setLineSpacingIsEscZ() {
    assertArrayEquals(new byte[] { 0x1B, 0x7A, 0x1E },
        StarLineSpacingCommands.SELECT_LINE_PITCH.getCode(new Amount(30)));
  }

  @Test
  void defaultLineSpacingIsEsc0() {
    assertArrayEquals(new byte[] { 0x1B, 0x30 }, StarLineSpacingCommands.LINE_PITCH_3MM.getCode());
  }

  @Test
  void dotFeedsAreEscJAndEscI() {
    assertArrayEquals(new byte[] { 0x1B, 0x4A, 0x0A },
        StarLineSpacingCommands.PRINT_AND_FEED_DOTS.getCode(new Amount(10)));
    assertArrayEquals(new byte[] { 0x1B, 0x49, 0x04 }, StarLineSpacingCommands.MICRO_FEED.getCode(new Amount(4)));
  }
}
