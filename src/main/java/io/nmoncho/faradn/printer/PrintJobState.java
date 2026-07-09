package io.nmoncho.faradn.printer;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

public class PrintJobState implements NodeProcessor<PrintJobState> {
  private final FontStyle fontStyle;
  private final TextStyle textStyle;
  private final String text;

  private PrintJobState(FontStyle fontStyle, TextStyle textStyle, String text) {
    this.fontStyle = fontStyle;
    this.textStyle = textStyle;
    this.text = text;
  }

  @Override
  public PrintJobState process(Element el) {
    final FontStyle f = fontStyle.process(el);
    final TextStyle t = textStyle.process(el);

    return this.fontStyle == f && this.textStyle == t ? this : new PrintJobState(f, t, "");
  }

  @Override
  public PrintJobState process(TextNode el) {
    String t = el.text();
    // TODO text should be processed by TextStyle for Upper, lower, Capitlized, etc.

    // TODO Maybe we need to check if the parent element respects white-spaces for this check
    return t.isBlank() ? this : new PrintJobState(fontStyle, textStyle, t);
  }

  public static PrintJobState initial() {
    return new PrintJobState(FontStyle.initial, TextStyle.initial, "");
  }

  @Override
  public String toString() {
    return "PrintJobState [fontStyle=" + fontStyle + ", textStyle=" + textStyle + ", text='" + text + "']";
  }

}
