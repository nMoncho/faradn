package io.nmoncho.faradn.document;

import io.nmoncho.faradn.Image;
import io.nmoncho.faradn.printer.FontStyle;
import io.nmoncho.faradn.printer.NodeProcessor;
import io.nmoncho.faradn.printer.TextStyle;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.Set;

public class PrintJobStates {

  public static final StyleState initialStyle = new StyleState(FontStyle.initial, TextStyle.initial);
  public static final State initial = initialStyle;

  public abstract static class State implements NodeProcessor<State> {

    public State process(StyleState currentStyle, Node node) {

    }

    @Override
    public State process(Node node) {
      State s = transition(this, node);

      if (node instanceof Element) {
        return s.process((Element) node);
      } else if (node instanceof TextNode) {
        return new TextState(node);
      } else {
        return this;
      }
    }
  }

  /**
   * Checks if a transition is required for the current element.
   *
   * @param current current state
   * @param node    node to process
   * @return correct state to use as processor
   */
  private static State transition(State current, Node node) {
    if (TextState.canProcess(node)) {
      return new TextState(node);
    } else if (StyleState.canProcess(node)) {

    } else if (ImageState.canProcess(node)) {
      return new ImageState(node);
    } else if (BarCodeState.canProcess(node)) {
      return new BarCodeState();
    } else {
      return current;
    }
  }

  public static final class StyleState extends State {
    private static final Set<String> styleElements = Set.of(
      "em", "i", "strong", "b"
    );

    private final FontStyle fontStyle;
    private final TextStyle textStyle;

    private StyleState(FontStyle fontStyle, TextStyle textStyle) {
      this.fontStyle = fontStyle;
      this.textStyle = textStyle;
    }

    public static boolean canProcess(Node node) {
      if (node instanceof Element) {
        Element el = (Element) node;
        String tag = el.normalName();
        return el.hasAttr("style") || styleElements.contains(tag);
      } else {
        return false;
      }
    }

    @Override
    public State process(Element el) {
      final FontStyle f = fontStyle.process(el);
      final TextStyle t = textStyle.process(el);

      final boolean equals = this.fontStyle == f && this.textStyle == t;

      return equals ? this : new StyleState(f, t);
    }

    @Override
    public String toString() {
      return "StyleState{" +
        "fontStyle=" + fontStyle +
        ", textStyle=" + textStyle +
        '}';
    }
  }

  public static final class TextState extends State {

    private final String text;

    TextState(Node node) {
      this.text = ((TextNode) node).text();
    }

    public static boolean canProcess(Node node) {
      return node instanceof TextNode && !((TextNode) node).isBlank();
    }

    public String getText() {
      return text;
    }

    @Override
    public String toString() {
      return "TextState{text='" + text + "'}";
    }
  }

  public static final class ImageState extends State {

    private final Image img;

    ImageState(Node node) {
      this.img = Image.fromNode((Element) node);
    }

    public static boolean canProcess(Node node) {
      if (node instanceof Element) {
        Element el = (Element) node;
        return el.nodeName().equals("img");
      } else {
        return false;
      }
    }

    public Image getImage() {
      return img;
    }

    @Override
    public String toString() {
      return "ImageState{}";
    }
  }

  public static final class BarCodeState extends State {
    public static boolean canProcess(Node node) {
      if (node instanceof Element) {
        Element el = (Element) node;
        return el.nodeName().equals("bar-code");
      } else {
        return false;
      }
    }
  }

}
