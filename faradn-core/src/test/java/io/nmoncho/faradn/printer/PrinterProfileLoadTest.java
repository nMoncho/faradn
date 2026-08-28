package io.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrinterProfileLoadTest {

  @Test
  void loadsTheTmT88vProfileFromTheDatabase() {
    PrinterProfile profile = PrinterProfile.load("TM-T88V").orElseThrow();

    assertEquals("Epson TM-T88V", profile.name());
    assertEquals(512, profile.dotsPerLine());
    assertEquals(42, profile.columns());
    assertEquals(180, profile.dpi());
    assertTrue(profile.supportsCut());
    assertEquals(CodePage.PC437, profile.codePage());
  }

  @Test
  void matchesDeviceNameCaseInsensitively() {
    assertTrue(PrinterProfile.load("tm-t88v").isPresent());
    assertTrue(PrinterProfile.load("Tm-T88v").isPresent());
    assertEquals(PrinterProfile.load("TM-T88V").orElseThrow().name(),
        PrinterProfile.load("tm-t88v").orElseThrow().name());
  }

  @Test
  void unknownDeviceNameIsEmpty() {
    assertTrue(PrinterProfile.load("no-such-printer-9000").isEmpty());
  }

  @Test
  void nullOrBlankNameIsEmpty() {
    assertTrue(PrinterProfile.load(null).isEmpty());
    assertTrue(PrinterProfile.load("").isEmpty());
    assertTrue(PrinterProfile.load("   ").isEmpty());
  }

  @Test
  void profileWithUnknownWidthIsNotUsable() {
    // The generic 'default' profile reports width "Unknown": it cannot render.
    assertTrue(PrinterProfile.load("default").isEmpty());
  }
}
