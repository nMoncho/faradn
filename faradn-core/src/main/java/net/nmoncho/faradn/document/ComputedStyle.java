package net.nmoncho.faradn.document;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.jsoup.nodes.Element;

import net.nmoncho.faradn.Utils;

/**
 * Fully resolved, printer-realizable style for a run of text.
 * <p>
 * Every property maps to an ESC/POS capability. Sizes are integer multiples
 * of the base character cell ({@code GS !} supports 1x to 8x), not points or
 * pixels. Italic uses the ESC/P {@code ESC 4}/{@code ESC 5} commands: printers
 * that support italic render it, and the rest ignore the command.
 * <p>
 * Instances are immutable. {@link #process(Element)} returns {@code this}
 * when an element does not change the style, so identity comparison can be
 * used to detect style transitions.
 */
public record ComputedStyle(boolean bold, boolean underline, int widthMultiple, int heightMultiple,
    Alignment alignment, boolean invert, int font, boolean italic, LineHeight lineHeight) {

  public static final int MIN_SIZE_MULTIPLE = 1;
  public static final int MAX_SIZE_MULTIPLE = 8;

  /**
   * CSS pixels that map to one step of {@code GS !} magnification (roughly CSS
   * {@code medium}). {@code font-size} sets an absolute magnification of the base
   * cell - {@code 16px}/{@code 1em}/{@code 100%} is 1&times;, {@code 32px} is
   * 2&times;, and so on - since the printer only offers integer 1&times;-8&times;
   * sizes, not points.
   */
  private static final double BASE_FONT_PX = 16.0;

  /**
   * Selected font, as an {@code ESC M} slot: 0 is Font&nbsp;A (the default), 1
   * Font&nbsp;B, 2 Font&nbsp;C, and so on. Which slots a printer actually has,
   * and their column budgets, are a profile concern; the style only records the
   * choice. See {@link net.nmoncho.faradn.printer.Font}.
   */
  public static final int DEFAULT_FONT = 0;

  /** The font slot {@code <small>} selects (Font B). */
  private static final int SMALL_FONT = 1;

  /**
   * Style at the root of a document: plain left-aligned text at base size, Font
   * A.
   */
  public static final ComputedStyle INITIAL = new ComputedStyle(false, false, 1, 1, Alignment.LEFT, false,
      DEFAULT_FONT, false, LineHeight.NORMAL);

  private static final Set<String> BOLD_TAGS = Set.of("b", "strong");
  private static final Set<String> ITALIC_TAGS = Set.of("em", "i");
  private static final Set<String> BOLD_CSS_WEIGHTS = Set.of("bold", "bolder", "600", "700", "800", "900");

  private static final Set<String> FONT_SIZE_KEYWORDS = Set.of(
      "xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "smaller", "larger");

  /**
   * {@code font: <system-keyword>} - use a system font; Farad'n has none, so
   * ignore it.
   */
  private static final Set<String> FONT_SYSTEM_KEYWORDS = Set.of(
      "caption", "icon", "menu", "message-box", "small-caption", "status-bar");

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
    int newFont = font;
    boolean newItalic = italic;
    LineHeight newLineHeight = lineHeight;

    // Tag defaults.
    final String tag = el.normalName();
    if (BOLD_TAGS.contains(tag)) {
      newBold = true;
    } else if (ITALIC_TAGS.contains(tag)) {
      newItalic = true;
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
    } else if (tag.equals("small")) {
      newFont = SMALL_FONT;
    }

    // The `font` shorthand sets style/weight/size/line-height/family at once and
    // resets the components it omits (per CSS). Applied before the longhands so an
    // explicit longhand (e.g. font-weight) still wins over it.
    final Optional<FontShorthand> fontShorthand = Utils.findStyleValue(el, "font").flatMap(ComputedStyle::parseFont);
    if (fontShorthand.isPresent()) {
      final FontShorthand shorthand = fontShorthand.get();
      newBold = shorthand.bold();
      newItalic = shorthand.italic();
      newWidth = shorthand.magnification();
      newHeight = shorthand.magnification();
      newLineHeight = shorthand.lineHeight();
      newFont = shorthand.font();
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

    final Optional<String> fontFamily = Utils.findStyleValue(el, "font-family");
    if (fontFamily.isPresent()) {
      final OptionalInt slot = fontSlotFromCss(fontFamily.get());
      if (slot.isPresent()) {
        newFont = slot.getAsInt();
      }
    }

    final Optional<String> fontStyle = Utils.findStyleValue(el, "font-style");
    if (fontStyle.isPresent()) {
      final String value = fontStyle.get().toLowerCase();
      newItalic = value.contains("italic") || value.contains("oblique");
    }

    // font-size scales the whole glyph uniformly, mapped to GS ! magnification
    // (both width and height). When present it overrides the tag's size, e.g. an
    // <h2 style="font-size: 300%"> becomes 3x rather than double-height.
    final Optional<String> fontSize = Utils.findStyleValue(el, "font-size");
    if (fontSize.isPresent()) {
      final OptionalInt multiple = fontSizeMultiple(fontSize.get());
      if (multiple.isPresent()) {
        newWidth = multiple.getAsInt();
        newHeight = multiple.getAsInt();
      }
    }

    // line-height inherits: keep the ancestor's value unless this element sets it.
    final Optional<String> lineHeightCss = Utils.findStyleValue(el, "line-height");
    if (lineHeightCss.isPresent()) {
      newLineHeight = LineHeight.parse(lineHeightCss.get());
    }

    final ComputedStyle computed = new ComputedStyle(newBold, newUnderline, newWidth, newHeight, newAlignment,
        invert, newFont, newItalic, newLineHeight);

    return equals(computed) ? this : computed;
  }

  /**
   * Maps a CSS {@code font-family} value to a font slot: {@code font-a} → 0,
   * {@code font-b} → 1, {@code font-c} → 2, and so on. The first recognized
   * family in the list wins; other names (real font stacks) are ignored.
   */
  /**
   * Maps a CSS {@code font-size} to a {@code GS !} magnification
   * (1&times;-8&times;,
   * width and height together). Keywords, {@code %}, {@code em}/{@code rem} and a
   * unit-less number are ratios of the base size;
   * {@code px}/{@code pt}/{@code in}/
   * {@code cm}/{@code mm} convert via {@link #BASE_FONT_PX}. Sub-1&times; sizes
   * clamp to 1&times; (the printer can't shrink the base font). Empty when the
   * value can't be understood, so the tag/inherited size stands.
   */
  private static OptionalInt fontSizeMultiple(String value) {
    final String v = value.strip().toLowerCase();
    if (v.isEmpty()) {
      return OptionalInt.empty();
    }
    switch (v) {
      case "xx-small":
      case "x-small":
      case "small":
      case "smaller":
      case "medium":
      case "normal":
        return OptionalInt.of(clampMultiple(1));
      case "large":
      case "larger":
        return OptionalInt.of(clampMultiple(2));
      case "x-large":
        return OptionalInt.of(clampMultiple(3));
      case "xx-large":
        return OptionalInt.of(clampMultiple(4));
      default:
        break;
    }
    try {
      final double ratio;
      if (v.endsWith("%")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 1).strip()) / 100.0;
      } else if (v.endsWith("rem")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 3).strip());
      } else if (v.endsWith("em")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 2).strip());
      } else if (v.endsWith("px")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 2).strip()) / BASE_FONT_PX;
      } else if (v.endsWith("pt")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 2).strip()) * 96.0 / 72.0 / BASE_FONT_PX;
      } else if (v.endsWith("in")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 2).strip()) * 96.0 / BASE_FONT_PX;
      } else if (v.endsWith("cm")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 2).strip()) * 96.0 / 2.54 / BASE_FONT_PX;
      } else if (v.endsWith("mm")) {
        ratio = Double.parseDouble(v.substring(0, v.length() - 2).strip()) * 96.0 / 25.4 / BASE_FONT_PX;
      } else {
        ratio = Double.parseDouble(v); // unit-less: a direct multiple
      }
      return OptionalInt.of(clampMultiple((int) Math.round(ratio)));
    } catch (NumberFormatException ignored) {
      return OptionalInt.empty();
    }
  }

  private static int clampMultiple(int multiple) {
    return Math.max(MIN_SIZE_MULTIPLE, Math.min(MAX_SIZE_MULTIPLE, multiple));
  }

  /** The components of a {@code font} shorthand that Farad'n realizes. */
  private record FontShorthand(boolean bold, boolean italic, int magnification, LineHeight lineHeight, int font) {
  }

  /**
   * Parses the CSS {@code font} shorthand:
   * {@code [<style> || <variant> || <weight> || <stretch>]? <size>[/<line-height>] <family>}.
   * A valid value needs a font-size and a font-family; the pieces Farad'n
   * understands (italic, bold, size, line-height, font slot) are extracted and
   * the rest reset to their defaults, as the shorthand requires. Empty for a
   * system-font keyword or a malformed value, so the individual longhands (and
   * the inherited style) stand.
   */
  private static Optional<FontShorthand> parseFont(String value) {
    final String raw = value.strip().toLowerCase();
    if (raw.isEmpty() || FONT_SYSTEM_KEYWORDS.contains(raw)) {
      return Optional.empty();
    }
    // Collapse any spaces around the size/line-height slash so it stays one token.
    final String[] tokens = raw.replaceAll("\\s*/\\s*", "/").split("\\s+");

    boolean bold = false;
    boolean italic = false;
    int i = 0;
    for (; i < tokens.length && !looksLikeFontSize(tokens[i]); i++) {
      // Leading style/variant/weight/stretch tokens; only italic and bold map.
      if (tokens[i].equals("italic") || tokens[i].equals("oblique")) {
        italic = true;
      } else if (BOLD_CSS_WEIGHTS.contains(tokens[i])) {
        bold = true;
      }
    }
    if (i >= tokens.length || i + 1 >= tokens.length) {
      return Optional.empty(); // font-size and font-family are both required
    }

    final String sizeToken = tokens[i];
    final int slash = sizeToken.indexOf('/');
    final OptionalInt magnification = fontSizeMultiple(slash >= 0 ? sizeToken.substring(0, slash) : sizeToken);
    if (magnification.isEmpty()) {
      return Optional.empty(); // malformed size -> ignore the whole shorthand
    }
    final LineHeight lineHeight = slash >= 0 ? LineHeight.parse(sizeToken.substring(slash + 1)) : LineHeight.NORMAL;
    final String family = String.join(" ", Arrays.copyOfRange(tokens, i + 1, tokens.length));
    final int fontSlot = fontSlotFromCss(family).orElse(DEFAULT_FONT);

    return Optional.of(new FontShorthand(bold, italic, magnification.getAsInt(), lineHeight, fontSlot));
  }

  /**
   * Whether a {@code font}-shorthand token is the font-size: a size keyword, a
   * {@code size/line-height} pair, or a number carrying a unit. A bare number is
   * a font-weight, not a size.
   */
  private static boolean looksLikeFontSize(String token) {
    if (FONT_SIZE_KEYWORDS.contains(token) || token.indexOf('/') >= 0) {
      return true;
    }
    if (token.isEmpty()) {
      return false;
    }
    final char first = token.charAt(0);
    if (!Character.isDigit(first) && first != '.') {
      return false;
    }
    for (int c = 0; c < token.length(); c++) {
      final char ch = token.charAt(c);
      if (!Character.isDigit(ch) && ch != '.') {
        return true; // a unit follows the number
      }
    }
    return false; // bare number -> a weight
  }

  private static OptionalInt fontSlotFromCss(String value) {
    for (String token : value.toLowerCase().split(",")) {
      final String name = token.strip().replace("\"", "").replace("'", "");
      if (name.length() == 6 && name.startsWith("font-")) {
        final char letter = name.charAt(5);
        if (letter >= 'a' && letter <= 'z') {
          return OptionalInt.of(letter - 'a');
        }
      }
    }
    return OptionalInt.empty();
  }
}
