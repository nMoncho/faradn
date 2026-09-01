package net.nmoncho.faradn;

import java.util.Optional;
import java.util.OptionalInt;

import org.jsoup.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

  public static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final String STYLE_ATTR = "style";

  /**
   * Finds a CSS style value (e.g. `font-weight`)
   *
   * @param node
   *        node to inspect
   * @param name
   *        CSS attribute name
   * @return some CSS value, if present, otherwise empty
   */
  public static Optional<String> findStyleValue(Node node, String name) {
    final String attr = node.attr(STYLE_ATTR);
    if (attr.isBlank()) {
      return Optional.empty();
    }

    for (String declaration : attr.split(";")) {
      final int colon = declaration.indexOf(':');
      if (colon > 0) {
        final String property = declaration.substring(0, colon).trim();
        final String value = declaration.substring(colon + 1).trim();
        if (property.equalsIgnoreCase(name) && !value.isEmpty()) {
          return Optional.of(value);
        }
      }
    }

    return Optional.empty();
  }

  /** Millimetres per inch, for converting physical CSS lengths to dots. */
  private static final float MM_PER_INCH = 25.4f;

  /**
   * Converts a CSS length to printer dots. Supported units: {@code px} (1:1 with
   * dots), {@code mm} and {@code cm} (via {@code dpi}), and {@code %} (a fraction
   * of {@code referenceDots}). A unit-less number is rejected - dot positions are
   * always explicit about their unit.
   *
   * @param value
   *        the CSS length, e.g. {@code "512px"}, {@code "80mm"}, {@code "3cm"},
   *        {@code "50%"}; may be {@code null}
   * @param dpi
   *        the printer resolution in dots per inch, used for
   *        {@code mm}/{@code cm}
   * @param referenceDots
   *        the extent that {@code 100%} maps to, used for {@code %}
   * @return the length in dots (rounded), or empty when {@code value} is null,
   *         blank, unparseable, or carries an unsupported/absent unit
   */
  public static OptionalInt lengthToDots(String value, int dpi, int referenceDots) {
    if (value == null) {
      return OptionalInt.empty();
    }
    final String v = value.strip().toLowerCase();
    if (v.isEmpty()) {
      return OptionalInt.empty();
    }
    try {
      if (v.endsWith("px")) {
        return OptionalInt.of(Math.round(Float.parseFloat(v.substring(0, v.length() - 2).strip())));
      } else if (v.endsWith("mm")) {
        final float mm = Float.parseFloat(v.substring(0, v.length() - 2).strip());
        return OptionalInt.of(Math.round(mm * dpi / MM_PER_INCH));
      } else if (v.endsWith("cm")) {
        final float cm = Float.parseFloat(v.substring(0, v.length() - 2).strip());
        return OptionalInt.of(Math.round(cm * 10f * dpi / MM_PER_INCH));
      } else if (v.endsWith("%")) {
        final float pct = Float.parseFloat(v.substring(0, v.length() - 1).strip());
        return OptionalInt.of(Math.round(pct / 100f * referenceDots));
      }
    } catch (NumberFormatException ignored) {
      log.debug("Couldn't parse CSS length [{}]", value);
    }
    return OptionalInt.empty();
  }

  /**
   * Parses an integer attribute
   *
   * @param node
   *        attribute holder
   * @param attribute
   *        attribute name
   * @return some integer if it could be parsed, empty otherwise
   */
  public static Optional<Integer> parseAttribute(Node node, String attribute) {
    return Optional
        .of(node.attr(attribute))
        .filter(attr -> !attr.isBlank())
        .flatMap(attr -> {
          try {
            return Optional.of(Integer.parseInt(attr));
          } catch (Throwable t) {
            log.debug("Couldn't parse attribute [{}] in node [{}]", attribute, node, t);
            return Optional.empty();
          }
        });
  }
}
