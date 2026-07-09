package io.nmoncho.faradn.document;

import java.util.Optional;
import java.util.Set;

import org.jsoup.nodes.Element;

import io.nmoncho.faradn.Utils;

/**
 * Fully resolved, printer-realizable style for a run of text.
 * <p>
 * Every property maps to an ESC/POS capability: there is no italic (most
 * ESC/POS printers have no italic command) and sizes are integer multiples
 * of the base character cell ({@code GS !} supports 1x to 8x), not points
 * or pixels.
 * <p>
 * Instances are immutable. {@link #process(Element)} returns {@code this}
 * when an element does not change the style, so identity comparison can be
 * used to detect style transitions.
 */
public record ComputedStyle(boolean bold, boolean underline, int widthMultiple, int heightMultiple,
    Alignment alignment, boolean invert) {

  public static final int MIN_SIZE_MULTIPLE = 1;
  public static final int MAX_SIZE_MULTIPLE = 8;

  /** Style at the root of a document: plain left-aligned text at base size. */
  public static final ComputedStyle INITIAL = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false);

  private static final Set<String> BOLD_TAGS = Set.of("b", "strong");
  private static final Set<String> BOLD_CSS_WEIGHTS = Set.of("bold", "bolder", "600", "700", "800", "900");

  public enum Alignment {
    LEFT, CENTER, RIGHT;

    static Optional<Alignment> fromCss(String value) {
      switch (value.toLowerCase()) {
        case "left":
        case "start":
          return Optional.of(LEFT);
        case "center":
          return Optional.of(CENTER);
        case "right":
        case "end":
          return Optional.of(RIGHT);
        default:
          return Optional.empty();
      }
    }
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
  }

  /**
   * Computes the style resulting from entering {@code el}, inheriting from
   * this style. Tag defaults are applied first, then inline CSS, so
   * {@code <b style="font-weight: normal">} is not bold.
   *
   * @param el
   *        element being entered
   * @return {@code this} if the element changes nothing, otherwise a new style
   */
  public ComputedStyle process(Element el) {
    boolean newBold = bold;
    boolean newUnderline = underline;
    int newWidth = widthMultiple;
    int newHeight = heightMultiple;
    Alignment newAlignment = alignment;

    // Tag defaults. <em>/<i> are deliberately absent: ESC/POS has no italic.
    final String tag = el.normalName();
    if (BOLD_TAGS.contains(tag)) {
      newBold = true;
    } else if (tag.equals("u")) {
      newUnderline = true;
    } else if (tag.equals("h1")) {
      newBold = true;
      newWidth = 2;
      newHeight = 2;
    } else if (tag.equals("h2")) {
      newBold = true;
      newHeight = 2;
    } else if (tag.equals("h3")) {
      newBold = true;
    } else if (tag.equals("center")) {
      newAlignment = Alignment.CENTER;
    }

    // Inline CSS overrides tag defaults
    final Optional<String> fontWeight = Utils.findStyleValue(el, "font-weight");
    if (fontWeight.isPresent()) {
      newBold = BOLD_CSS_WEIGHTS.contains(fontWeight.get().toLowerCase());
    }

    final Optional<String> textDecoration = Utils
        .findStyleValue(el, "text-decoration")
        .or(() -> Utils.findStyleValue(el, "text-decoration-line"));
    if (textDecoration.isPresent()) {
      newUnderline = textDecoration.get().toLowerCase().contains("underline");
    }

    final Optional<Alignment> textAlign = Utils
        .findStyleValue(el, "text-align")
        .flatMap(Alignment::fromCss);
    if (textAlign.isPresent()) {
      newAlignment = textAlign.get();
    }

    final ComputedStyle computed = new ComputedStyle(newBold, newUnderline, newWidth, newHeight, newAlignment,
        invert);

    return equals(computed) ? this : computed;
  }
}
