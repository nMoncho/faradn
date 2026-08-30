package net.nmoncho.faradn.document;

/**
 * A barcode, expressed in the HTML source as a {@code <bar-code>} custom
 * element, e.g. {@code <bar-code symbology="code128">12345678</bar-code>}.
 * <p>
 * The symbology is carried as declared; whether it is supported (and which
 * {@code GS k} variant realizes it) is a per-printer renderer concern.
 * {@link #options} carry the source element's rendering attributes (height,
 * module size, HRI position, QR error correction).
 */
public record Barcode(String data, String symbology, ComputedStyle.Alignment alignment, BarcodeOptions options)
    implements
      Block {

  public static final String DEFAULT_SYMBOLOGY = "code128";

  public Barcode {
    if (data == null || data.isEmpty()) {
      throw new IllegalArgumentException("data must not be null or empty");
    }
    if (symbology == null || symbology.isEmpty()) {
      symbology = DEFAULT_SYMBOLOGY;
    }
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
    if (options == null) {
      options = BarcodeOptions.DEFAULT;
    }
  }

  /** A barcode with default rendering options. */
  public Barcode(String data, String symbology, ComputedStyle.Alignment alignment) {
    this(data, symbology, alignment, BarcodeOptions.DEFAULT);
  }
}
