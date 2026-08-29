package io.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Test;

class PrinterProfileLoadTest {

  @Test
  void loadsTheTmT88vProfileFromTheDatabase() {
    PrinterProfile profile = PrinterProfile.load("TM-T88V").orElseThrow();

    assertEquals("Epson TM-T88V", profile.name());
    assertEquals(512, profile.dotsPerLine());
    assertEquals(42, profile.columns()); // Font A
    assertEquals(56, profile.font(1).columns()); // Font B
    assertEquals(180, profile.dpi());
    assertTrue(profile.supportsCut());
    assertEquals(0, profile.codePage().id());
    assertEquals(Charset.forName("IBM437"), profile.codePage().charset());
  }

  @Test
  void loadsAllFontsFromTheDatabase() {
    // The Citizen CT-S651 lists three fonts in the database (48 / 64 / 72 columns).
    PrinterProfile profile = PrinterProfile.load("CT-S651").orElseThrow();

    assertEquals(3, profile.fonts().size());
    assertEquals(48, profile.font(0).columns()); // Font A
    assertEquals(64, profile.font(1).columns()); // Font B
    assertEquals(72, profile.font(2).columns()); // Font C
    assertEquals(48, profile.columns()); // default is Font A
    assertEquals(48, profile.font(99).columns()); // an absent slot falls back to the default font
  }

  @Test
  void codePagesComeFromTheDatabaseAndExcludeMultibyte() {
    PrinterProfile profile = PrinterProfile.load("TM-T88V").orElseThrow();
    List<Integer> ids = profile.codePages().stream().map(CodePage::id).toList();

    assertFalse(profile.codePages().isEmpty());
    assertTrue(ids.contains(0), "default page (CP437, slot 0) is present");
    assertTrue(ids.contains(16), "WPC1252 (slot 16) is present");
    assertTrue(ids.contains(17), "PC866 (slot 17) is present");
    assertFalse(ids.contains(1), "multi-byte CP932 (slot 1) is not selectable via ESC t");
    assertTrue(profile.codePages().contains(profile.codePage()), "the default is one of the pages");
    for (CodePage page : profile.codePages()) {
      assertTrue(page.charset().newEncoder().maxBytesPerChar() <= 1.0f, "single-byte only: " + page);
    }
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
