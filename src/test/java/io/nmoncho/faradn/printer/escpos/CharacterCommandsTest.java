package io.nmoncho.faradn.printer.escpos;

import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands;

import static org.junit.jupiter.api.Assertions.*;

public class CharacterCommandsTest {

  @Test
  void testHumanReadableCodes() {
    assertEquals("ESC -", CharacterCommands.UNDERLINE.toString());
    assertEquals("ESC E", CharacterCommands.EMPHASIZED.toString());
    assertEquals("ESC G", CharacterCommands.DOUBLE_STRIKE.toString());
    assertEquals("ESC {", CharacterCommands.TURN_UPSIDE.toString());
    assertEquals("GS B", CharacterCommands.REVERSE_BACKGROUND.toString());
    assertEquals("GS b", CharacterCommands.SMOOTHING.toString());

    assertEquals("ESC  ", CharacterCommands.SET_RIGHT_SIDE_SPACING.toString());
    assertEquals("ESC !", CharacterCommands.SELECT_PRINT_MODE.toString());

    assertEquals("ESC %", CharacterCommands.USER_DEFINED_CHARACTER_SET.toString());
    assertEquals("ESC ?", CharacterCommands.CANCEL_USER_DEFINED_CHARACTERS.toString());
  }

}
