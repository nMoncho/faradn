package io.nmoncho.faradn.printer;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

@SuppressWarnings("unchecked")
public interface NodeProcessor<T extends NodeProcessor<T>> {

  /**
   * Process a {@link org.jsoup.nodes.Node} returning a modified copy of
   * itself, if by processing this element, something has changed.
   * <b>note:</b> only {@link org.jsoup.nodes.Element} nad
   * {@link org.jsoup.nodes.TextNode}
   * will be processed. Any other child will be ignored.
   *
   * @param node
   *        node to process
   * @return this instance if nothing changed, modified copy if it did.
   */
  default T process(Node node) {
    if (node instanceof Element) {
      return process((Element) node);
    } else if (node instanceof TextNode) {
      return process((TextNode) node);
    } else {
      return (T) this;
    }
  }

  /**
   * Process a {@link org.jsoup.nodes.Element} returning a modified copy of
   * itself, if by processing this element, something has changed.
   *
   * @param el
   *        element to process
   * @return this instance if nothing changed, modified copy if it did.
   */
  default T process(Element el) {
    return (T) this;
  }

  /**
   * Process a {@link org.jsoup.nodes.Element} returning a modified copy of
   * itself, if by processing this element, something has changed.
   *
   * @param text
   *        text node to process
   * @return this instance if nothing changed, modified copy if it did.
   */
  default T process(TextNode text) {
    return (T) this;
  }
}
