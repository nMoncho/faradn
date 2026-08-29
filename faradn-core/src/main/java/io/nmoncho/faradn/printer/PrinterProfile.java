package io.nmoncho.faradn.printer;

import java.util.List;
import java.util.Optional;

/**
 * The realizable capabilities of a target printer that the renderer must
 * respect: how wide the paper is, how many characters fit on a line, its
 * resolution, and whether it can cut. These are physical facts about the
 * device, not rendering preferences.
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
   * Loads a printer profile from the bundled escpos-printer-db capability
   * database, matched case-insensitively by device name (the database key or
   * its {@code name} field, e.g. {@code "TM-T88V"}).
   *
   * @param name
   *        the device name to look up
   * @return the profile, or empty when no usable profile matches the name
   */
  static Optional<PrinterProfile> load(String name) {
    return CapabilityProfiles.find(name);
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
    if (codePages == null || codePages.isEmpty()) {
      throw new IllegalArgumentException("codePages must not be empty");
    }
    if (fonts == null || fonts.isEmpty()) {
      throw new IllegalArgumentException("fonts must not be empty");
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
        return fontList;
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
        return pages;
      }
    };
  }
}
