package io.nmoncho.faradn.printer.escpos;

import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands.PrintMode;

import static org.junit.jupiter.api.Assertions.*;

public class PrintModeTest {

  @Test
  void builderWithoutParameters() {
    PrintMode pm = PrintMode.builder().build();

    assertTrue(pm.isFont1());
    assertFalse(pm.isFont2());
    assertFalse(pm.isEmphasized());
    assertFalse(pm.isDoubleHeight());
    assertFalse(pm.isDoubleWidth());
    assertFalse(pm.isUnderline());
  }

  @Test
  void builderWithFont2() {
    PrintMode pm = PrintMode
        .builder()
        .withCharacterFont2()
        .build();

    assertTrue(pm.isFont2());
    assertFalse(pm.isFont1());
  }

  @Test
  void builderWithSwitchingFont() {
    PrintMode pm = PrintMode
        .builder()
        .withCharacterFont2()
        .withCharacterFont1()
        .build();

    assertTrue(pm.isFont1());
    assertFalse(pm.isFont2());
  }

  @Test
  void builderWithAllEnabled() {
    PrintMode pm = PrintMode
        .builder()
        .withCharacterFont2()
        .withEmphasized(true)
        .withDoubleHeight(true)
        .withDoubleWeight(true)
        .withUnderline(true)
        .build();

    assertFalse(pm.isFont1());
    assertTrue(pm.isFont2());
    assertTrue(pm.isEmphasized());
    assertTrue(pm.isDoubleHeight());
    assertTrue(pm.isDoubleWidth());
    assertTrue(pm.isUnderline());
  }

}
