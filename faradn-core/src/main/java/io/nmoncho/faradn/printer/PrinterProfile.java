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

  /** Characters per line at the base font (Font A). */
  int columns();

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
   * @param columns
   *        Characters per line at the base font (Font A)
   * @param dpi
   *        Print resolution in dots per inch
   * @param supportsCut
   *        Whether the printer has an autocutter
   * @param codePages
   *        the code pages the printer can select, preference order (must be
   *        non-empty); the default is the slot&nbsp;0 page, or the first
   * @return a profile backed by the given values
   */
  static PrinterProfile of(String name, int dotsPerLine, int columns, int dpi, boolean supportsCut,
      List<CodePage> codePages) {
    if (codePages == null || codePages.isEmpty()) {
      throw new IllegalArgumentException("codePages must not be empty");
    }
    final List<CodePage> pages = List.copyOf(codePages);
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
      public int columns() {
        return columns;
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
