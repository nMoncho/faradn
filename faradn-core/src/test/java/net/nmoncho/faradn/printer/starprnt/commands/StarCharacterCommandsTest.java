//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.starprnt.commands.StarCharacterCommands.FontSlot;
import net.nmoncho.faradn.printer.starprnt.commands.StarCharacterCommands.StarCharacterSize;

class StarCharacterCommandsTest {

  @Test
  void boldIsTwoOpcodesWithNoParameter() {
    assertArrayEquals(new byte[] { 0x1B, 0x45 }, StarCharacterCommands.BOLD_ON.getCode());
    assertArrayEquals(new byte[] { 0x1B, 0x46 }, StarCharacterCommands.BOLD_OFF.getCode());
    assertEquals("ESC E", StarCharacterCommands.BOLD_ON.toString());
    assertEquals("ESC F", StarCharacterCommands.BOLD_OFF.toString());
  }

  @Test
  void underlineTogglesWithParameter() {
    assertArrayEquals(new byte[] { 0x1B, 0x2D, 0x01 }, StarCharacterCommands.UNDERLINE.turnOn());
    assertArrayEquals(new byte[] { 0x1B, 0x2D, 0x00 }, StarCharacterCommands.UNDERLINE.turnOff());
  }

  @Test
  void invertUsesEsc4And5NotItalic() {
    // 1B 34 / 1B 35 mean invert on Star (they are italic in ESC/POS).
    assertArrayEquals(new byte[] { 0x1B, 0x34 }, StarCharacterCommands.INVERT_ON.getCode());
    assertArrayEquals(new byte[] { 0x1B, 0x35 }, StarCharacterCommands.INVERT_OFF.getCode());
  }

  @Test
  void upsideDownIsBareControlBytes() {
    assertArrayEquals(new byte[] { 0x0F }, StarCharacterCommands.UPSIDE_DOWN_ON.getCode());
    assertArrayEquals(new byte[] { 0x12 }, StarCharacterCommands.UPSIDE_DOWN_OFF.getCode());
  }

  @Test
  void sizeIsEscIHeightThenWidthZeroBased() {
    // ESC i n1 n2 : n1 = height-1, n2 = width-1. width=2, height=3 -> n1=2, n2=1.
    assertArrayEquals(new byte[] { 0x1B, 0x69, 0x02, 0x01 },
        StarCharacterCommands.SELECT_CHARACTER_SIZE.getCode(new StarCharacterSize(2, 3)));
    // 1x1 -> both zero.
    assertArrayEquals(new byte[] { 0x1B, 0x69, 0x00, 0x00 },
        StarCharacterCommands.SELECT_CHARACTER_SIZE.getCode(new StarCharacterSize(1, 1)));
  }

  @Test
  void sizeClampsToSixTimes() {
    assertThrows(IllegalArgumentException.class, () -> new StarCharacterSize(7, 1));
    assertThrows(IllegalArgumentException.class, () -> new StarCharacterSize(1, 0));
  }

  @Test
  void fontSelectIsEscRsF() {
    assertArrayEquals(new byte[] { 0x1B, 0x1E, 0x46, 0x00 },
        StarCharacterCommands.SELECT_FONT.getCode(new FontSlot(0)));
    assertArrayEquals(new byte[] { 0x1B, 0x1E, 0x46, 0x01 },
        StarCharacterCommands.SELECT_FONT.getCode(new FontSlot(1)));
  }
}
