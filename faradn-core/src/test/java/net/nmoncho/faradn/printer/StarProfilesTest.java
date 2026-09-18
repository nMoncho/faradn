//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.starprnt.StarPrntRenderer;

class StarProfilesTest {

  // The overseas (single-byte) TSP100IV is loaded straight from the merged
  // capability database; only the Japanese Kanji variant is authored in code.
  private static PrinterProfile database() {
    return PrinterProfile.load("TSP100IV").orElseThrow();
  }

  @Test
  void databaseTsp100ivCarriesStarNativeSelectors() {
    final PrinterProfile p = database();

    assertEquals("Star TSP100IV", p.name());
    assertEquals(203, p.dpi());
    assertEquals(576, p.dotsPerLine());
    assertEquals(PrinterLanguage.STAR_PRNT, p.language());
    assertTrue(p.supportsCut());
    assertEquals(48, p.columns()); // Font A 12x24 -> 48 columns (the default)
    assertEquals(64, p.font(1).columns()); // Font B 9x24 -> 64 columns

    // ids are Star ESC GS t n selectors, not db slots; CP437 (selector 1) is the
    // default page selected at job start, and the native selectors resolve to the
    // same charsets the hand-authored profile used.
    assertEquals(1, p.codePage().id());
    final List<Integer> ids = p.codePages().stream().map(CodePage::id).toList();
    assertTrue(ids.containsAll(List.of(1, 4, 5, 6, 9, 10, 32)), "native selectors present: " + ids);
  }

  @Test
  void japaneseProfileDerivesFromDatabaseAndAddsKanjiRom() {
    final PrinterProfile jp = StarProfiles.tsp143ivJapanese();
    final PrinterProfile base = database();

    assertEquals("Star TSP143IV (Japanese)", jp.name());
    assertEquals(PrinterLanguage.STAR_PRNT, jp.language());
    // Geometry, fonts and code pages are inherited from the database profile.
    assertEquals(base.dotsPerLine(), jp.dotsPerLine());
    assertEquals(base.dpi(), jp.dpi());
    assertEquals(base.supportsCut(), jp.supportsCut());
    assertEquals(base.codePages().stream().map(CodePage::id).toList(),
        jp.codePages().stream().map(CodePage::id).toList());
    // Only the Kanji ROM is added on top.
    assertEquals(java.nio.charset.Charset.forName("windows-31j"), jp.kanjiCharset().orElseThrow());
    assertTrue(base.kanjiCharset().isEmpty(), "overseas unit has no Kanji ROM");
  }

  @Test
  void bothProfilesSelectTheStarBackend() {
    assertInstanceOf(StarPrntRenderer.class, Renderers.forProfile(database()));
    assertInstanceOf(StarPrntRenderer.class, Renderers.forProfile(StarProfiles.tsp143ivJapanese()));
  }
}
