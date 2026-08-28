package io.nmoncho.faradn.printer;

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

  /** The character code page the renderer selects and encodes text for. */
  CodePage codePage();

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
   * @param codePage
   *        he character code page the renderer selects and encodes text for
   * @return
   */
  static PrinterProfile of(String name, int dotsPerLine, int columns, int dpi, boolean supportsCut, CodePage codePage) {
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
        return codePage;
      }
    };
  }
}
