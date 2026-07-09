package io.nmoncho.faradn.printer;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import io.nmoncho.faradn.Utils;

public class FontStyle implements NodeProcessor<FontStyle> {
  public static final FontStyle initial = new FontStyle("serif", 1, FontWeight.NORMAL, FontStyleAttr.NORMAL,
      TextTransformAttr.NONE, 1.2);

  private final String family;
  private final int size;
  private final FontWeight weight;
  private final FontStyleAttr style;
  private final TextTransformAttr textTransform;
  private final double lineHeight;

  public FontStyle(String family, int size, FontWeight weight, FontStyleAttr style, TextTransformAttr textTransform,
      double lineHeight) {
    this.family = family;
    this.size = size;
    this.weight = weight;
    this.style = style;
    this.textTransform = textTransform;
    this.lineHeight = lineHeight;
  }

  @Override
  public FontStyle process(Element el) {
    final FontWeight fw = FontWeight.parse(el);
    final FontStyleAttr fs = FontStyleAttr.parse(el);
    final TextTransformAttr tt = TextTransformAttr.parse(el);
    final FontStyle f = new FontStyle(family,
        size,
        fw != FontWeight.INHERIT ? fw : weight,
        fs != FontStyleAttr.INHERIT ? fs : style,
        tt != TextTransformAttr.INHERIT ? tt : textTransform,
        lineHeight);

    return equalFont(f) ? this : f;
  }

  public boolean equalFont(FontStyle other) {
    if (other.weight != FontWeight.INHERIT && other.weight != weight) {
      return false;
    } else if (other.style != FontStyleAttr.INHERIT && other.style != style) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return "Font [family=" + family + ", size=" + size + ", weight=" + weight + ", style=" + style + ", lineHeight="
        + lineHeight + ", size=" + size + "]";
  }

  enum FontWeight {
    INHERIT("inherit"), INITIAL("initial"), NORMAL("normal"), BOLD("bold"), BOLDER("bolder"), LIGHTER("lighter"), W100(
        "100"), W200("200"), W300("300"), W400("400"), W500("500"), W600("600"), W700("700"), W800("800"), W900("900");

    private final String value;

    FontWeight(String value) {
      this.value = value;
    }

    static FontWeight parse(Element el) {
      final Tag t = el.tag();
      final String tagName = t.getName();
      final String styleAttr = el.attr("style");

      if (tagName.equals("b") || tagName.equals("strong")) {
        return BOLD;
      } else if (!styleAttr.isEmpty()) {
        return Utils
            .findStyleValue(el, "font-weight")
            .map(FontWeight::fromValue)
            .orElse(getDefault());
      } else {
        return getDefault();
      }
    }

    public String value() {
      return value;
    }

    public static FontWeight getDefault() {
      return INHERIT;
    }

    public static FontWeight fromValue(String value) {
      for (FontWeight v : values()) {
        if (v.value.equals(value)) {
          return v;
        }
      }

      return getDefault();
    }
  }

  enum FontStyleAttr {
    INHERIT("inherit"), INITIAL("initial"), NORMAL("normal"), ITALIC("italic"), OBLIQUE("oblique");

    private final String value;

    FontStyleAttr(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }

    static FontStyleAttr parse(Element el) {
      final Tag t = el.tag();
      final String tagName = t.getName();
      final String styleAttr = el.attr("style");

      if (tagName.equals("i") || tagName.equals("em")) {
        return ITALIC;
      } else if (!styleAttr.isEmpty()) {
        return Utils
            .findStyleValue(el, "font-style")
            .map(FontStyleAttr::fromValue)
            .orElse(getDefault());
      } else {
        return getDefault();
      }
    }

    public static FontStyleAttr getDefault() {
      return INHERIT;
    }

    public static FontStyleAttr fromValue(String value) {
      for (FontStyleAttr v : values()) {
        if (v.value.equals(value)) {
          return v;
        }
      }

      return getDefault();
    }
  }

  enum TextTransformAttr {
    INHERIT("inherit"), NONE("none"), UPPERCASE("uppercase"), LOWERCASE("lowercase"), CAPITALIZE("capitalize");

    private final String value;

    TextTransformAttr(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }

    static TextTransformAttr parse(Element el) {
      final Tag t = el.tag();
      final String styleAttr = el.attr("style");

      if (!styleAttr.isEmpty()) {
        return Utils
            .findStyleValue(el, "text-transform")
            .map(TextTransformAttr::fromValue)
            .orElse(getDefault());
      } else {
        return getDefault();
      }
    }

    public static TextTransformAttr getDefault() {
      return INHERIT;
    }

    public static TextTransformAttr fromValue(String value) {
      for (TextTransformAttr v : values()) {
        if (v.value.equals(value)) {
          return v;
        }
      }

      return getDefault();
    }
  }
}
