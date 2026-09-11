//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.starprnt.commands.StarPrintPositionCommands.Justification;

class StarPrintPositionCommandsTest {

  @Test
  void justificationIsEscGsA() {
    // ESC GS a n : extra leading GS versus ESC/POS ESC a n.
    assertArrayEquals(new byte[] { 0x1B, 0x1D, 0x61, 0x00 },
        StarPrintPositionCommands.SELECT_JUSTIFICATION.getCode(Justification.LEFT));
    assertArrayEquals(new byte[] { 0x1B, 0x1D, 0x61, 0x01 },
        StarPrintPositionCommands.SELECT_JUSTIFICATION.getCode(Justification.CENTER));
    assertArrayEquals(new byte[] { 0x1B, 0x1D, 0x61, 0x02 },
        StarPrintPositionCommands.SELECT_JUSTIFICATION.getCode(Justification.RIGHT));
  }
}
