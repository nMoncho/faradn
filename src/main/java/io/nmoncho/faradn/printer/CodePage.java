package io.nmoncho.faradn.printer;

import java.nio.charset.Charset;

/**
 * An ESC/POS character code page: the {@code ESC t n} selector paired with the
 * Java {@link Charset} that encodes text the same way the printer decodes it.
 * Characters outside the page encode to the charset's replacement byte
 * ({@code '?'}), which is the intended fallback for unmappable glyphs.
 */
public enum CodePage {

  PC437(0, "IBM437"), PC850(2, "IBM850"), WPC1252(16, "windows-1252"), PC858(19, "IBM00858");

  private final int id;
  private final Charset charset;

  CodePage(int id, String charsetName) {
    this.id = id;
    this.charset = Charset.forName(charsetName);
  }

  /** The {@code ESC t} selector number for this page. */
  public int id() {
    return id;
  }

  /** The Java charset that matches this page's byte encoding. */
  public Charset charset() {
    return charset;
  }
}
