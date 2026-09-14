//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Hand-authored profiles for the Zebra ZD421 label printer, which is absent
 * from
 * escpos-printer-db (that database only covers ESC/POS receipt printers, and
 * its
 * geometry checks are receipt-shaped). The ZD421 speaks both ZPL II and EPL2,
 * so
 * there is one profile per language and resolution, for both ZPL II and EPL2.
 * <p>
 * Values follow the ZD421 User Guide: 832 dots printable at 203&nbsp;dpi,
 * 1280&nbsp;dots at 300&nbsp;dpi. There is no autocutter on the base model
 * ({@code supportsCut() == false}); media defaults to direct thermal with gap
 * tracking (the ZD421d out of the box), inherited from {@link PrinterProfile}'s
 * defaults. Unlike a receipt printer a label has no character grid, so the font
 * only carries a base char-cell width in dots (the {@code 1x} text size, which
 * authors scale up via {@code font-size}); it is derived as
 * {@code dotsPerLine / columns} by {@code LabelLayout}. Text encoding is UTF-8
 * via the renderer's {@code ^CI28}, so the code page is a placeholder the ZPL
 * renderer does not consult.
 */
public final class ZebraProfiles {

  // ZPL: char cell ~20 dots wide at both resolutions (a compact, readable 1x label font).
  private static final int COLUMNS_203 = 42; // 832 / 42 ~= 20 dots
  private static final int COLUMNS_300 = 64; // 1280 / 64 = 20 dots

  // EPL: columns chosen so dotsPerLine/columns ~= the internal font 3 char width
  // (12 dots at 203, 18 at 300), so LabelLayout offsets line up with the glyphs.
  private static final int EPL_COLUMNS_203 = 69; // 832 / 69 ~= 12 dots
  private static final int EPL_COLUMNS_300 = 71; // 1280 / 71 ~= 18 dots

  private ZebraProfiles() {
  }

  /** The Zebra ZD421 at 203&nbsp;dpi, rendering ZPL II. */
  public static PrinterProfile zd421Zpl203() {
    return zpl("Zebra ZD421 (ZPL, 203dpi)", 832, COLUMNS_203, 203);
  }

  /** The Zebra ZD421 at 300&nbsp;dpi, rendering ZPL II. */
  public static PrinterProfile zd421Zpl300() {
    return zpl("Zebra ZD421 (ZPL, 300dpi)", 1280, COLUMNS_300, 300);
  }

  private static PrinterProfile zpl(String name, int dotsPerLine, int columns, int dpi) {
    return PrinterProfile.of(name, dotsPerLine, List.of(new Font(0, columns)), dpi, false,
        List.of(new CodePage(28, StandardCharsets.UTF_8)), PrinterLanguage.ZPL);
  }

  /** The Zebra ZD421 at 203&nbsp;dpi, rendering EPL2. */
  public static PrinterProfile zd421Epl203() {
    return epl("Zebra ZD421 (EPL, 203dpi)", 832, EPL_COLUMNS_203, 203);
  }

  /** The Zebra ZD421 at 300&nbsp;dpi, rendering EPL2. */
  public static PrinterProfile zd421Epl300() {
    return epl("Zebra ZD421 (EPL, 300dpi)", 1280, EPL_COLUMNS_300, 300);
  }

  private static PrinterProfile epl(String name, int dotsPerLine, int columns, int dpi) {
    // EPL2 is single-byte: the code page is a real DOS page (CP437), the id its
    // I-command selector, unlike the ZPL profiles' UTF-8 placeholder.
    return PrinterProfile.of(name, dotsPerLine, List.of(new Font(0, columns)), dpi, false,
        List.of(new CodePage(0, Charset.forName("IBM437"))), PrinterLanguage.EPL);
  }
}
