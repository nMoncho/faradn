//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BuiltinProfilesTest {

  @Test
  void loadsTheZebraZplProfilesByName() {
    assertEquals("Zebra ZD421 (ZPL, 203dpi)", PrinterProfile.load("zd421-zpl-203").orElseThrow().name());
    assertEquals("Zebra ZD421 (ZPL, 300dpi)", PrinterProfile.load("zd421-zpl-300").orElseThrow().name());
  }

  @Test
  void loadsTheStarProfileByNameToo() {
    // Previously code-only; the registry makes it reachable via load()/--profile.
    assertEquals(PrinterLanguage.STAR_PRNT, PrinterProfile.load("star-tsp143iv").orElseThrow().language());
  }

  @Test
  void nameMatchIsCaseInsensitive() {
    assertTrue(PrinterProfile.load("ZD421-ZPL-203").isPresent());
    assertTrue(PrinterProfile.load("Star-TSP143IV").isPresent());
  }

  @Test
  void availableMergesDatabaseAndBuiltins() {
    final List<String> available = PrinterProfile.available();

    assertTrue(available.contains("zd421-zpl-203"), "built-in listed");
    assertTrue(available.contains("zd421-zpl-300"), "built-in listed");
    assertTrue(available.contains("star-tsp143iv"), "built-in listed");
    assertTrue(available.contains("TM-T88V"), "database entry still listed");
    for (String name : available) {
      assertTrue(PrinterProfile.load(name).isPresent(), name + " should resolve with load()");
    }
  }

  @Test
  void databaseNameStillResolvesAndUnknownIsEmpty() {
    assertTrue(PrinterProfile.load("TM-T88V").isPresent());
    assertTrue(PrinterProfile.load("no-such-printer-9000").isEmpty());
  }
}
