package io.nmoncho.faradn;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

  public static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final String STYLE_ATTR = "style";
  public static final String STYLE_ATTR_PATTERN = "\\s*\\:\\s*(.+?)[;$]";

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
    return Optional
        .of(node.attr(STYLE_ATTR))
        .filter(attr -> !attr.isBlank())
        .flatMap(attr -> {
          Pattern pattern = Pattern.compile(name + STYLE_ATTR_PATTERN); // TODO could add a cache to avoid re-compiling
          Matcher matcher = pattern.matcher(attr);

          if (matcher.find()) {
            return Optional.of(matcher.group(1));
          } else {
            return Optional.empty();
          }
        });
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
