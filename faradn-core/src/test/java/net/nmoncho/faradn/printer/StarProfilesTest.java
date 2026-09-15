//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Document;
import net.nmoncho.faradn.Printer;
import net.nmoncho.faradn.printer.starprnt.StarPrntRenderer;
import net.nmoncho.faradn.transport.DumpTransport;

class StarProfilesTest {

  @Test
  void tsp143ivGeometryAndLanguage() {
    final PrinterProfile p = StarProfiles.tsp143iv();

    assertEquals("Star TSP143IV", p.name());
    assertEquals(203, p.dpi());
    assertEquals(576, p.dotsPerLine());
    assertEquals(PrinterLanguage.STAR_PRNT, p.language());
    assertTrue(p.supportsCut());

    // Font A 12x24 -> 48 columns (the default); Font B 9x24 -> 64 columns.
    assertEquals(48, p.columns());
    assertEquals(48, p.font(0).columns());
    assertEquals(64, p.font(1).columns());
  }

  @Test
  void codePagesCarryStarNativeSelectorNumbers() {
    final PrinterProfile p = StarProfiles.tsp143iv();

    // ids are Star ESC GS t n selectors, not db slots; CP437 is first (the default).
    assertEquals(List.of(1, 4, 5, 6, 9, 10, 32), p.codePages().stream().map(CodePage::id).toList());
    assertEquals(1, p.codePage().id()); // CP437 selected at job start
  }

  @Test
  void overseasProfileHasNoKanjiRom() {
    // The default TSP143IV is a single-byte (overseas) unit: no Kanji font.
    assertTrue(StarProfiles.tsp143iv().kanjiCharset().isEmpty());
  }

  @Test
  void japaneseProfileCarriesAShiftJisKanjiRom() {
    final PrinterProfile p = StarProfiles.tsp143ivJapanese();

    assertEquals("Star TSP143IV (Japanese)", p.name());
    assertEquals(PrinterLanguage.STAR_PRNT, p.language());
    assertEquals(java.nio.charset.Charset.forName("windows-31j"), p.kanjiCharset().orElseThrow());
  }

  @Test
  void profileSelectsTheStarBackend() {
    assertInstanceOf(StarPrntRenderer.class, Renderers.forProfile(StarProfiles.tsp143iv()));
  }

  @Test
  void printsThroughTheStarBackendEndToEnd() {
    // The whole HTML -> IR -> Star renderer -> transport pipeline, selected by the
    // profile's language, matches rendering the Star backend directly.
    final PrinterProfile p = StarProfiles.tsp143iv();
    final Document doc = Document.from("<h1>Hi</h1><p>Line</p>");
    final DumpTransport transport = new DumpTransport();

    Printer.print(transport, doc, p);

    final byte[] expected = new StarPrntRenderer(p).render(doc.blocks(p.dpi()));
    assertArrayEquals(expected, transport.bytes());
    // Sanity: the job starts with ESC @ (init) and the Star ESC GS t code-page select.
    assertArrayEquals(new byte[] { 0x1B, 0x40, 0x1B, 0x1D, 0x74, 0x01 },
        java.util.Arrays.copyOfRange(transport.bytes(), 0, 6));
  }
}
