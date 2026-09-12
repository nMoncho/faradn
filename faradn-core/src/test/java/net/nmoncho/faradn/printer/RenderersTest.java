//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.epl.EplRenderer;
import net.nmoncho.faradn.printer.starprnt.StarPrntRenderer;
import net.nmoncho.faradn.printer.zpl.ZplRenderer;

class RenderersTest {

  private static final List<Font> FONTS = List.of(new Font(0, 42));
  private static final List<CodePage> PAGES = List.of(new CodePage(0, Charset.forName("IBM437")));

  private static PrinterProfile profile(PrinterLanguage language) {
    return PrinterProfile.of("Test", 512, FONTS, 180, true, PAGES, language);
  }

  @Test
  void defaultLanguageIsEscPos() {
    // A plain of(...) profile (no language given) stays ESC/POS, so nothing existing changes.
    assertEquals(PrinterLanguage.ESC_POS,
        PrinterProfile.of("Legacy", 512, FONTS, 180, true, PAGES).language());
  }

  @Test
  void forProfileSelectsEscPosByDefault() {
    assertInstanceOf(EscPosRenderer.class, Renderers.forProfile(profile(PrinterLanguage.ESC_POS)));
  }

  @Test
  void forProfileSelectsStarPrntForAStarProfile() {
    assertInstanceOf(StarPrntRenderer.class, Renderers.forProfile(profile(PrinterLanguage.STAR_PRNT)));
  }

  @Test
  void starRendererProducesAFramedJob() {
    // The Star renderer is implemented (Phase 3): an empty job still frames with
    // ESC @ init + code-page select and an end-of-job feed + cut.
    final Renderer star = Renderers.forProfile(profile(PrinterLanguage.STAR_PRNT));
    assertTrue(star.render(List.of()).length > 0);
  }

  @Test
  void forProfileSelectsZplForAZebraZplProfile() {
    assertInstanceOf(ZplRenderer.class, Renderers.forProfile(profile(PrinterLanguage.ZPL)));
  }

  @Test
  void forProfileSelectsEplForAZebraEplProfile() {
    assertInstanceOf(EplRenderer.class, Renderers.forProfile(profile(PrinterLanguage.EPL)));
  }

  @Test
  void labelRenderersAreStubsUntilTheirRenderPhase() {
    // Phase 0 wires renderer selection only; the ZPL/EPL render() bodies land in
    // Phases 3 and 5 (see PLAN_ZEBRA_ZD421.md).
    assertThrows(UnsupportedOperationException.class,
        () -> Renderers.forProfile(profile(PrinterLanguage.ZPL)).render(List.of()));
    assertThrows(UnsupportedOperationException.class,
        () -> Renderers.forProfile(profile(PrinterLanguage.EPL)).render(List.of()));
  }

  @Test
  void forProfileRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> Renderers.forProfile(null));
  }

  @Test
  void onlyEscPosSupportsRealtimeStatus() {
    assertTrue(PrinterLanguage.ESC_POS.supportsRealtimeStatus());
    assertFalse(PrinterLanguage.STAR_PRNT.supportsRealtimeStatus());
    assertFalse(PrinterLanguage.ZPL.supportsRealtimeStatus());
    assertFalse(PrinterLanguage.EPL.supportsRealtimeStatus());
  }
}
