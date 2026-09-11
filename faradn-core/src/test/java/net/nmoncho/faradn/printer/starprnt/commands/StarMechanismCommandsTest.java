//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class StarMechanismCommandsTest {

  @Test
  void cutIsEscD() {
    // ESC d 0 / ESC d 1 - ESC/POS ESC d is a line feed, so this must not be reused.
    assertArrayEquals(new byte[] { 0x1B, 0x64, 0x00 }, StarMechanismCommands.FULL_CUT.getCode());
    assertArrayEquals(new byte[] { 0x1B, 0x64, 0x01 }, StarMechanismCommands.PARTIAL_CUT.getCode());
  }

  @Test
  void drawerProgramsPulseThenFires() {
    // ESC BEL 5 50 (50 ms / 500 ms in 10 ms units) then BEL (drawer 1) / SUB (drawer 2).
    assertArrayEquals(new byte[] { 0x1B, 0x07, 0x05, 0x32, 0x07 }, StarMechanismCommands.DRAWER_KICK_1.getCode());
    assertArrayEquals(new byte[] { 0x1B, 0x07, 0x05, 0x32, 0x1A }, StarMechanismCommands.DRAWER_KICK_2.getCode());
  }
}
