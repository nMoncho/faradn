package io.nmoncho.faradn.printer;

import org.jsoup.nodes.Element;

import io.nmoncho.faradn.Utils;

import java.util.Locale;
import java.util.Set;

public class TextStyle implements NodeProcessor<TextStyle> {

  private static final Set<String> RTL;

  static {
    // we go through the new Locale(), then getLanguage(), because the language codes are not always stable, according to the Locale.getLanguage() documentation
    // stripExtensions() is used so only the language and not country specific codes are used. ie: "en" rather than "en_CA"

    RTL = Set.of(
        new Locale("ar").stripExtensions().getLanguage(), // Arabic
        new Locale("dv").stripExtensions().getLanguage(), // Divehi
        new Locale("fa").stripExtensions().getLanguage(), // Persian
        new Locale("ha").stripExtensions().getLanguage(), // Hausa
        new Locale("he").stripExtensions().getLanguage(), // Hebrew
        new Locale("ps").stripExtensions().getLanguage(), // Pushto
        new Locale("sd").stripExtensions().getLanguage(), // Sindhi
        new Locale("ug").stripExtensions().getLanguage(), // Uighur
        new Locale("ur").stripExtensions().getLanguage(), // Urdu
        new Locale("yi").stripExtensions().getLanguage() // Yiddish
    );
  }

  public static final TextStyle initial = new TextStyle(
      isRightToLeft(Locale.getDefault()) ? TextAlign.RIGHT : TextAlign.LEFT);

  private final TextAlign align;

  public TextStyle(TextAlign align) {
    this.align = align;
  }

  @Override
  public TextStyle process(Element el) {
    TextAlign a = TextAlign.parse(el);
    TextStyle t = new TextStyle(a != TextAlign.INHERIT ? a : align);

    return equalStyle(t) ? this : t;
  }

  public boolean equalStyle(TextStyle other) {
    return other.align == TextAlign.INHERIT || other.align == align;
  }

  @Override
  public String toString() {
    return "TextStyle [align=" + align + "]";
  }

  enum TextAlign {
    INHERIT("inherit"), INITIAL("initial"), LEFT("left"), CENTER("center"), RIGHT("right");

    private final String value;

    TextAlign(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }

    static TextAlign parse(Element el) {
      final String styleAttr = el.attr("style");

      if (!styleAttr.isEmpty()) {
        return Utils
            .findStyleValue(el, "text-align")
            .map(TextAlign::fromValue)
            .orElse(getDefault());
      } else {
        return getDefault();
      }
    }

    public static TextAlign getDefault() {
      return INHERIT;
    }

    public static TextAlign fromValue(String value) {
      for (TextAlign v : values()) {
        if (v.value.equals(value)) {
          return v;
        }
      }

      return getDefault();
    }
  }

  private static boolean isRightToLeft(Locale locale) {
    return RTL.contains(locale.stripExtensions().getLanguage());
  }
}
