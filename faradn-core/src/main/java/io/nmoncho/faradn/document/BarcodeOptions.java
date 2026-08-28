package io.nmoncho.faradn.document;

/**
 * Rendering options for a {@link Barcode}, parsed from the source element's
 * attributes ({@code height}, {@code module}, {@code hri}, {@code ec}).
 * <p>
 * Every field has a printer-agnostic default so a bare
 * {@code <bar-code>…</bar-code>} still renders. A {@link #moduleSize} of
 * {@code 0} means "use the symbology's built-in default", which differs
 * between 1D ({@code GS w}), QR and PDF417.
 */
public record BarcodeOptions(int heightDots, int moduleSize, Hri hri, QrEc qrEc) {

  /** Human-readable interpretation (HRI) text position, for 1D symbologies. */
  public enum Hri {
    NONE, ABOVE, BELOW, BOTH
  }

  /**
   * QR Code error-correction level, lowest ({@code L}, ~7%) to highest ({@code H}, ~30^).
   * See <a href="https://en.wikipedia.org/wiki/QR_code#Error_correction">Error Correction</a>.
   */
  public enum QrEc {
    L, M, Q, H
  }

  public static final BarcodeOptions DEFAULT = new BarcodeOptions(100, 0, Hri.BELOW, QrEc.M);

  public BarcodeOptions {
    if (heightDots < 1 || heightDots > 255) {
      throw new IllegalArgumentException("heightDots must be 1-255, got " + heightDots);
    }
    if (moduleSize < 0 || moduleSize > 16) {
      throw new IllegalArgumentException("moduleSize must be 0-16 (0 = symbology default), got " + moduleSize);
    }
    if (hri == null) {
      throw new IllegalArgumentException("hri must not be null");
    }
    if (qrEc == null) {
      throw new IllegalArgumentException("qrEc must not be null");
    }
  }
}
