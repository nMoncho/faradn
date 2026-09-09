package net.nmoncho.faradn.internal.html;

import java.util.Optional;

import org.jsoup.nodes.Node;

import net.nmoncho.faradn.Utils;

/**
 * Internal helpers for reading inline CSS and attributes off jsoup nodes while
 * building the IR.
 * <p>
 * <strong>Not public API.</strong> This class lives in an internal package so
 * that jsoup does not leak into the exported surface; it may change or move
 * without notice.
 */
public final class HtmlUtil {

  private HtmlUtil() {
  }

  /**
   * Finds a CSS style value (e.g. {@code font-weight}) in a node's inline
   * {@code style} attribute.
   *
   * @param node
   *        node to inspect
   * @param name
   *        CSS property name
   * @return some CSS value, if present, otherwise empty
   */
  public static Optional<String> findStyleValue(Node node, String name) {
    final String attr = node.attr(Utils.STYLE_ATTR);
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
   * Parses an integer attribute.
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
            Utils.log.debug("Couldn't parse attribute [{}] in node [{}]", attribute, node, t);
            return Optional.empty();
          }
        });
  }
}
