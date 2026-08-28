package io.nmoncho.faradn.printer;

import java.nio.charset.Charset;

/**
 * An ESC/POS character code page: the {@code ESC t n} selector paired with the
 * Java {@link Charset} that encodes text the same way the printer decodes it.
 * Characters outside the page encode to the charset's replacement byte
 * ({@code '?'}), which is the intended fallback for unmappable glyphs.
 * <p>
 * The declaration order is the renderer's switch-preference order: when a
 * character is not in the currently selected page, pages are tried in this
 * order (after the profile's default, which is always tried first) so a
 * broad-coverage page is chosen before an exotic one. See {@code
 * CodePageEncoder}.
 */
public enum CodePage {

  PC437(0, "IBM437"), // USA / standard Europe (the common default)
  WPC1252(16, "windows-1252"), // broad Western European, incl. the euro
  PC850(2, "IBM850"), // Multilingual Latin 1
  PC858(19, "IBM00858"), // Latin 1 + euro
  PC852(18, "IBM852"), // Latin 2 (Central European)
  PC866(17, "IBM866"), // Cyrillic
  PC860(3, "IBM860"), // Portuguese
  PC863(4, "IBM863"), // Canadian French
  PC865(5, "IBM865"); // Nordic

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
