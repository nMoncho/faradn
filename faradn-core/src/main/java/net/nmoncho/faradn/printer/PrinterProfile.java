//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The realizable capabilities of a target printer that the renderer must
 * respect: how wide the paper is, how many characters fit on a line, its
 * resolution, and whether it can cut. These are physical facts about the
 * device, not rendering preferences.
 * <p>
 * Profiles are immutable value objects and safe to share between threads.
 */
public interface PrinterProfile {

  /** Human-readable profile name, e.g. {@code "Epson TM-T88V"}. */
  String name();

  /** Printable width in dots (e.g. 512 for an 80&nbsp;mm TM-T88V). */
  int dotsPerLine();

  /**
   * The fonts this printer offers, ascending by {@code ESC M} slot. Slot&nbsp;0
   * (Font&nbsp;A) is always present; a printer may add Font&nbsp;B, Font&nbsp;C,
   * and so on, each narrower (more columns) than the last.
   */
  List<Font> fonts();

  /**
   * The font for a given {@code ESC M} slot, or the default font when the printer
   * has no such slot.
   */
  default Font font(int slot) {
    return fonts().stream().filter(f -> f.id() == slot).findFirst().orElseGet(this::defaultFont);
  }

  /**
   * The font selected at reset (slot&nbsp;0, Font&nbsp;A), or the lowest slot the
   * printer has.
   */
  default Font defaultFont() {
    return fonts().stream().filter(f -> f.id() == 0).findFirst().orElseGet(() -> fonts().get(0));
  }

  /** Characters per line at the base font (Font&nbsp;A). */
  default int columns() {
    return defaultFont().columns();
  }

  /** Print resolution in dots per inch. */
  int dpi();

  /** Whether the printer has an autocutter. */
  boolean supportsCut();

  /**
   * Whether the printer supports ESC/POS page mode ({@code ESC L … FF}), which
   * the renderer uses for positioned {@code Canvas} regions. The capability
   * database carries no such flag, so this defaults to {@code true} (all TM-class
   * and the vast majority of ESC/POS printers support it); override it to
   * {@code false} for a known-unsupported model so the renderer can warn.
   */
  default boolean supportsPageMode() {
    return true;
  }

  /**
   * Whether the printer can print 1D barcodes ({@code GS k}). The renderer still
   * emits barcodes regardless; a {@code false} is a hint (logged as a warning)
   * that the device may not decode them. Defaults to {@code true}; the capability
   * database sets it per model.
   */
  default boolean supportsBarcodes() {
    return true;
  }

  /**
   * Whether the printer can print QR codes ({@code GS ( k}). See
   * {@link #supportsBarcodes()}.
   */
  default boolean supportsQrCode() {
    return true;
  }

  /**
   * Whether the printer can print PDF417 codes ({@code GS ( k}). See
   * {@link #supportsBarcodes()}.
   */
  default boolean supportsPdf417() {
    return true;
  }

  /**
   * Whether the printer can print raster images ({@code GS v 0}). See
   * {@link #supportsBarcodes()}.
   */
  default boolean supportsImages() {
    return true;
  }

  /**
   * The code page selected at reset (the initial {@code ESC t}); usually
   * slot&nbsp;0.
   */
  CodePage codePage();

  /**
   * The code pages this printer can switch to via {@code ESC t}, in selection
   * preference order. The renderer switches among these per character so glyphs
   * outside {@link #codePage()} still encode faithfully; {@link #codePage()} is
   * one of them.
   */
  List<CodePage> codePages();

  /**
   * The command language this profile renders with, which selects the
   * {@link Renderer} (see {@link Renderers#forProfile(PrinterProfile)}). Defaults
   * to {@link PrinterLanguage#ESC_POS} so every existing profile - and every
   * {@link #of} profile - is unchanged. The protocol is arguably a device fact
   * alongside the physical capabilities above, and a non-ESC/POS device (a Star
   * printer) overrides it.
   */
  default PrinterLanguage language() {
    return PrinterLanguage.ESC_POS;
  }

  /**
   * The print method of the media, direct thermal or thermal transfer. Consulted
   * only by the label backends (ZPL {@code ^MT}, EPL options); the receipt
   * backends ignore it. Defaults to {@link MediaType#DIRECT_THERMAL} (the ZD421d
   * out of the box), so every existing and every {@link #of} profile is
   * unchanged.
   */
  default MediaType mediaType() {
    return MediaType.DIRECT_THERMAL;
  }

  /**
   * How the printer finds the top of each label. Consulted only by the label
   * backends (ZPL {@code ^MN}, EPL {@code Q} gap); the receipt backends ignore
   * it. Defaults to {@link MediaTracking#GAP} (die-cut labels, the common case).
   */
  default MediaTracking mediaTracking() {
    return MediaTracking.GAP;
  }

  /**
   * Loads a printer profile by name, matched case-insensitively. Resolves first
   * against the bundled escpos-printer-db capability database (by device name or
   * {@code name} field, e.g. {@code "TM-T88V"}), then against the built-in
   * hand-authored profiles for models the database does not cover (e.g.
   * {@code "star-tsp143iv"}, {@code "zd421-zpl-203"}).
   *
   * @param name
   *        the device name to look up
   * @return the profile, or empty when no usable profile matches the name
   */
  static Optional<PrinterProfile> load(String name) {
    return CapabilityProfiles.find(name).or(() -> BuiltinProfiles.find(name));
  }

  /**
   * The names of every profile that resolves with {@link #load(String)}: the
   * capability-database entries that load to a usable profile (a plausible
   * printable width and Font&nbsp;A column budget) plus the built-in
   * hand-authored profiles, sorted and case-insensitively de-duplicated.
   *
   * @return the loadable profile names
   */
  static List<String> available() {
    final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    names.addAll(CapabilityProfiles.available());
    names.addAll(BuiltinProfiles.names());
    return List.copyOf(names);
  }

  /**
   * Helper method to create a {@link PrinterProfile} on the fly.
   *
   * @param name
   *        Human-readable profile name, e.g. {@code "Epson TM-T88V"}
   * @param dotsPerLine
   *        Printable width in dots (e.g. 512 for an 80&nbsp;mm TM-T88V)
   * @param fonts
   *        the printer's fonts, ascending by slot (must be non-empty and
   *        include slot&nbsp;0, Font&nbsp;A)
   * @param dpi
   *        Print resolution in dots per inch
   * @param supportsCut
   *        Whether the printer has an autocutter
   * @param codePages
   *        the code pages the printer can select, preference order (must be
   *        non-empty); the default is the slot&nbsp;0 page, or the first
   * @return a profile backed by the given values
   */
  static PrinterProfile of(String name, int dotsPerLine, List<Font> fonts, int dpi,
      boolean supportsCut, List<CodePage> codePages) {
    return of(name, dotsPerLine, fonts, dpi, supportsCut, codePages, PrinterLanguage.ESC_POS);
  }

  /**
   * Helper method to create a {@link PrinterProfile} on the fly for a specific
   * command language (see {@link #language()}). Equivalent to
   * {@link #of(String, int, List, int, boolean, List)} but with an explicit
   * language, so a Star profile can be minted without the capability database.
   *
   * @param name
   *        Human-readable profile name, e.g. {@code "Star TSP143IV"}
   * @param dotsPerLine
   *        Printable width in dots
   * @param fonts
   *        the printer's fonts, ascending by slot (must be non-empty and
   *        include slot&nbsp;0, Font&nbsp;A)
   * @param dpi
   *        Print resolution in dots per inch
   * @param supportsCut
   *        Whether the printer has an autocutter
   * @param codePages
   *        the code pages the printer can select, preference order (must be
   *        non-empty)
   * @param language
   *        the command language the profile renders with
   * @return a profile backed by the given values
   */
  static PrinterProfile of(String name, int dotsPerLine, List<Font> fonts, int dpi,
      boolean supportsCut, List<CodePage> codePages, PrinterLanguage language) {
    if (codePages == null || codePages.isEmpty()) {
      throw new IllegalArgumentException("codePages must not be empty");
    }
    if (fonts == null || fonts.isEmpty()) {
      throw new IllegalArgumentException("fonts must not be empty");
    }
    if (language == null) {
      throw new IllegalArgumentException("language must not be null");
    }

    final List<CodePage> pages = List.copyOf(codePages);
    final List<Font> fontList = List.copyOf(fonts);
    final CodePage defaultPage = pages.stream().filter(page -> page.id() == 0).findFirst().orElse(pages.get(0));

    return new PrinterProfile() {

      @Override
      public String name() {
        return name;
      }

      @Override
      public int dotsPerLine() {
        return dotsPerLine;
      }

      @Override
      public List<Font> fonts() {
        return List.copyOf(fontList);
      }

      @Override
      public int dpi() {
        return dpi;
      }

      @Override
      public boolean supportsCut() {
        return supportsCut;
      }

      @Override
      public CodePage codePage() {
        return defaultPage;
      }

      @Override
      public List<CodePage> codePages() {
        return List.copyOf(pages);
      }

      @Override
      public PrinterLanguage language() {
        return language;
      }
    };
  }
}
