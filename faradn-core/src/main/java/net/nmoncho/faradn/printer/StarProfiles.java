//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Hand-authored profiles for Star Micronics printers, which the bundled
 * escpos-printer-db capability database does not usefully cover (the TSP100IV
 * family is absent, and the Star entries that are present are ESC/POS-emulation
 * profiles whose code-page slots are Epson {@code ESC t} numbers, not Star
 * native {@code ESC GS t} numbers).
 * <p>
 * Reach a Star printer through {@link Renderers#forProfile(PrinterProfile)} (or
 * {@link net.nmoncho.faradn.Printer}) with one of these profiles, e.g.
 * {@code Renderers.forProfile(StarProfiles.tsp143iv())}.
 */
public final class StarProfiles {

  private StarProfiles() {
  }

  /**
   * The Star TSP143IV (TSP100IV family): an 80&nbsp;mm receipt printer at
   * 203&nbsp;dpi (576 printable dots), Font&nbsp;A 12&times;24 (48 columns) and
   * Font&nbsp;B 9&times;24 (64 columns), with an autocutter, rendering with
   * {@link PrinterLanguage#STAR_PRNT}.
   * <p>
   * The code pages carry <strong>Star native {@code ESC GS t n}</strong>
   * selector numbers as their {@link CodePage#id()} (CP437=1, CP858=4, CP852=5,
   * CP860=6, CP865=9, CP866=10, CP1252=32), not database slots. CP437 is first,
   * so it is the default page selected at job start.
   *
   * @return the TSP143IV profile
   */
  public static PrinterProfile tsp143iv() {
    return PrinterProfile.of(
        "Star TSP143IV",
        576,
        List.of(new Font(0, 48), new Font(1, 64)),
        203,
        true,
        List.of(
            page(1, "IBM437"), // CP437
            page(4, "IBM00858"), // CP858
            page(5, "IBM852"), // CP852
            page(6, "IBM860"), // CP860
            page(9, "IBM865"), // CP865
            page(10, "IBM866"), // CP866
            page(32, "windows-1252")), // CP1252
        PrinterLanguage.STAR_PRNT);
  }

  /**
   * A {@link CodePage} keyed by its Star native {@code ESC GS t} selector number.
   */
  private static CodePage page(int starSelector, String charset) {
    return new CodePage(starSelector, Charset.forName(charset));
  }
}
