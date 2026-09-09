package net.nmoncho.faradn.document;

/**
 * Fully resolved, printer-realizable style for a run of text.
 * <p>
 * Every property maps to an ESC/POS capability. Sizes are integer multiples
 * of the base character cell ({@code GS !} supports 1x to 8x), not points or
 * pixels. Italic uses the ESC/P {@code ESC 4}/{@code ESC 5} commands: printers
 * that support italic render it, and the rest ignore the command.
 * <p>
 * Instances are immutable and compared by value, so identity comparison detects
 * style transitions. Resolving an HTML element into a {@code ComputedStyle} is
 * done by the internal style resolver, so this stays a pure value type.
 */
public record ComputedStyle(boolean bold, boolean underline, int widthMultiple, int heightMultiple,
    Alignment alignment, boolean invert, int font, boolean italic, LineHeight lineHeight) {

  public static final int MIN_SIZE_MULTIPLE = 1;
  public static final int MAX_SIZE_MULTIPLE = 8;

  /**
   * Selected font, as an {@code ESC M} slot: 0 is Font&nbsp;A (the default), 1
   * Font&nbsp;B, 2 Font&nbsp;C, and so on. Which slots a printer actually has,
   * and their column budgets, are a profile concern; the style only records the
   * choice. See {@link net.nmoncho.faradn.printer.Font}.
   */
  public static final int DEFAULT_FONT = 0;

  /**
   * Style at the root of a document: plain left-aligned text at base size, Font
   * A.
   */
  public static final ComputedStyle INITIAL = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false,
      DEFAULT_FONT, false, LineHeight.NORMAL);

  public enum Alignment {
    LEFT, CENTER, RIGHT;
  }

  public ComputedStyle {
    if (widthMultiple < MIN_SIZE_MULTIPLE || widthMultiple > MAX_SIZE_MULTIPLE) {
      throw new IllegalArgumentException(
          "widthMultiple must be in [" + MIN_SIZE_MULTIPLE + ", " + MAX_SIZE_MULTIPLE + "], got " + widthMultiple);
    }
    if (heightMultiple < MIN_SIZE_MULTIPLE || heightMultiple > MAX_SIZE_MULTIPLE) {
      throw new IllegalArgumentException(
          "heightMultiple must be in [" + MIN_SIZE_MULTIPLE + ", " + MAX_SIZE_MULTIPLE + "], got " + heightMultiple);
    }
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
    if (font < 0) {
      throw new IllegalArgumentException("font slot must be >= 0, got " + font);
    }
    if (lineHeight == null) {
      throw new IllegalArgumentException("lineHeight must not be null");
    }
  }

  /** A style at the base font (Font A), not italic, default line height. */
  public ComputedStyle(boolean bold, boolean underline, int widthMultiple, int heightMultiple,
      Alignment alignment, boolean invert) {
    this(bold, underline, widthMultiple, heightMultiple, alignment, invert, DEFAULT_FONT, false);
  }

  /** A style at the given font, not italic, default line height. */
  public ComputedStyle(boolean bold, boolean underline, int widthMultiple, int heightMultiple,
      Alignment alignment, boolean invert, int font) {
    this(bold, underline, widthMultiple, heightMultiple, alignment, invert, font, false);
  }

  /** A style at the given font and italic, default line height. */
  public ComputedStyle(boolean bold, boolean underline, int widthMultiple, int heightMultiple,
      Alignment alignment, boolean invert, int font, boolean italic) {
    this(bold, underline, widthMultiple, heightMultiple, alignment, invert, font, italic, LineHeight.NORMAL);
  }
}
