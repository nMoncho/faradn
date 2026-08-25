package io.nmoncho.faradn.document;

/**
 * A run of text with its fully computed style.
 * <p>
 * Styles are resolved at build time (no inheritance left to compute), so a
 * renderer only has to diff consecutive runs to emit minimal state-change
 * commands.
 */
public record TextRun(String text, ComputedStyle style) {

  public TextRun {
    if (text == null || text.isEmpty()) {
      throw new IllegalArgumentException("text must not be null or empty");
    }
    if (style == null) {
      throw new IllegalArgumentException("style must not be null");
    }
  }
}
