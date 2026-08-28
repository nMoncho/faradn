package io.nmoncho.faradn.printer;

import java.nio.charset.Charset;

/**
 * A code page a printer can select via {@code ESC t n}: the selector number
 * ({@code id}) paired with the Java {@link Charset} that encodes text the way
 * the printer decodes that page.
 * <p>
 * Which selector maps to which encoding is printer-specific, so the set of
 * pages is not hardcoded here - it comes from the capability database, exposed
 * as {@link PrinterProfile#codePages()}. The renderer selects among a profile's
 * pages per character (see {@code CodePageEncoder}).
 */
public record CodePage(int id, Charset charset) {

  public CodePage {
    if (id < 0 || id > 255) {
      throw new IllegalArgumentException("code page id must be 0-255, got " + id);
    }
    if (charset == null) {
      throw new IllegalArgumentException("charset must not be null");
    }
  }
}
