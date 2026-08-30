package net.nmoncho.faradn;

import java.util.Optional;

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
