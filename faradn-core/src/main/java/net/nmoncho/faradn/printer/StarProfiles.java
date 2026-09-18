//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.nio.charset.Charset;

/**
 * The one Star Micronics profile the capability database cannot express. Every
 * other Star model is loaded from the bundled database by name (e.g.
 * {@code PrinterProfile.load("TSP100IV")} or the {@code "star-tsp143iv"}
 * alias):
 * {@code scripts/fetch_capabilities.py} merges the Star native
 * {@code star-prnt}
 * profiles from ReceiptPrinterEncoder, whose code-page slots are the Star
 * native
 * {@code ESC GS t} selector numbers.
 * <p>
 * The Japanese TSP143IV is the exception: no database records a printer's
 * Kanji ROM, so that one field is supplied here, over the database profile.
 */
public final class StarProfiles {

  private StarProfiles() {
  }

  /**
   * The Star TSP143IV as sold into a Japanese (multi-byte) market: the database
   * {@code TSP100IV} profile plus a Shift-JIS Kanji ROM, so CJK characters print
   * through StarPRNT's UTF-8 Kanji path instead of falling back to {@code '?'}.
   * Overseas (single-byte) units have no Kanji font, so the plain
   * {@code TSP100IV}
   * database profile leaves {@link PrinterProfile#kanjiCharset()} empty.
   * <p>
   * Geometry, fonts and code pages come from the database profile; only the
   * Kanji charset (and the model-specific name) are added here. The database
   * profile's barcode/QR/image capabilities all default to supported, so the
   * rebuild via {@link PrinterProfile#of} preserves them.
   *
   * @return the TSP100IV profile with a Shift-JIS Kanji ROM
   */
  public static PrinterProfile tsp143ivJapanese() {
    final PrinterProfile base = PrinterProfile.load("TSP100IV")
        .orElseThrow(() -> new IllegalStateException("TSP100IV profile missing from the capability database"));
    return PrinterProfile.of(
        "Star TSP143IV (Japanese)",
        base.dotsPerLine(),
        base.fonts(),
        base.dpi(),
        base.supportsCut(),
        base.codePages(),
        base.language(),
        Charset.forName("windows-31j")); // Shift-JIS Kanji ROM
  }
}
