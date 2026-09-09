//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

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

  @Test
  void surfacesCapabilityFlagsFromTheDatabase() {
    PrinterProfile profile = PrinterProfile.load("TM-T88V").orElseThrow();

    assertTrue(profile.supportsBarcodes());
    assertTrue(profile.supportsQrCode());
    assertTrue(profile.supportsPdf417());
    assertTrue(profile.supportsImages());
  }

  @Test
  void rejectsProfilesWithInconsistentGeometry() {
    // AF-240 / OCD-100 are ESC/POS customer displays (100-dot "width"); TSP800
    // lists 42 Font A columns on an 833-dot line and NT-80-V-UL 12 columns on 576
    // dots - character grids no real printer has. The verbatim escpos-printer-db
    // import carries these, so the sanity check keeps them from silently
    // corrupting layout.
    assertTrue(PrinterProfile.load("AF-240").isEmpty());
    assertTrue(PrinterProfile.load("OCD-100").isEmpty());
    assertTrue(PrinterProfile.load("TSP800").isEmpty());
    assertTrue(PrinterProfile.load("NT-80-V-UL").isEmpty());
  }

  @Test
  void availableListsOnlyLoadableProfiles() {
    List<String> available = PrinterProfile.available();

    assertFalse(available.isEmpty());
    assertTrue(available.contains("TM-T88V"), "the verified model is listed");
    assertFalse(available.contains("TSP800"), "a geometry-inconsistent profile is not offered");
    for (String name : available) {
      assertTrue(PrinterProfile.load(name).isPresent(), name + " should resolve with load()");
    }
  }
}
