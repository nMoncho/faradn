package net.nmoncho.faradn.printer;

/**
 * A character font the printer offers: its {@code ESC M} selector ({@code id},
 * 0-based - 0 is Font&nbsp;A, 1 Font&nbsp;B, 2 Font&nbsp;C, …) paired with how
 * many characters fit on a line in that font.
 * <p>
 * Fonts are printer-specific and come from the capability database (see
 * {@link PrinterProfile#fonts()}); a narrower font has a higher {@code columns}
 * count. Runs select a font by its slot ({@code id}), which the renderer emits
 * as {@code ESC M id}.
 */
public record Font(int id, int columns) {

  public Font {
    if (id < 0) {
      throw new IllegalArgumentException("font id must be >= 0, got " + id);
    }
    if (columns < 1) {
      throw new IllegalArgumentException("font columns must be >= 1, got " + columns);
    }
  }
}
