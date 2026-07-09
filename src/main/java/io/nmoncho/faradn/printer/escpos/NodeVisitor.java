package io.nmoncho.faradn.printer.escpos;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import io.nmoncho.faradn.Document;
import io.nmoncho.faradn.Printer;
import io.nmoncho.faradn.PrintingException;
import io.nmoncho.faradn.printer.PrintJobState;

public class NodeVisitor implements org.jsoup.select.NodeVisitor {

  private final Document doc;
  // private final EscPosPrinter printer;
  private final ByteArrayOutputStream baos;

  private final Map<Element, PrintJobState> statesByElement;
  private final List<PrintJobState> states;

  public NodeVisitor(Document doc) {
    this.doc = doc;
    // this.printer = printer;
    this.baos = new ByteArrayOutputStream();
    this.states = new LinkedList<>();
    this.statesByElement = new HashMap<>();
  }

  public List<PrintJobState> states() {
    doc.getDoc().body().traverse(this);
    return states;
  }

  byte[] toPrintCodes() {
    doc.getDoc().traverse(this);

    return baos.toByteArray();
  }

  @Override
  public void head(Node node, int depth) {
    try {
      PrintJobState state = states.isEmpty() ? PrintJobState.initial() : states.get(states.size() - 1);
      if (states.isEmpty()) {
        this.states.add(state);

        // FIXME I don't like this here! Duplicateed from line 62
        if (node instanceof Element) {
          Element el = (Element) node;
          this.statesByElement.put(el, state); // FIXME this is wrong if `body` defines some styles
        }
      }

      if (node instanceof Element) {
        Element el = (Element) node;

        PrintJobState newState = state.process(el);
        if (state != newState) {
          states.add(newState);
          this.statesByElement.put(el, newState);
        }
      } else if (node instanceof TextNode) {
        PrintJobState newState = state.process((TextNode) node);
        if (state != newState) {
          states.add(newState);
        }
      }
    } catch (Throwable t) {
      throw new PrintingException("Something went wrong trying to append to buffer", t);
    }
  }

  @Override
  public void tail(Node node, int depth) {
    try {
      if (node instanceof Element && node.hasParent()) {
        // Find previous state to rollback to
        // As an improvement, we could rollback only if the element being processed pushed a new state
        Element parent = (Element) node.parent();
        PrintJobState oldState = statesByElement.get(parent);
        while (oldState == null && parent.hasParent()) {
          parent = parent.parent();
          oldState = statesByElement.get(parent);
        }

        PrintJobState state = states.get(states.size() - 1);
        if (oldState != null && !state.equals(oldState)) {
          states.add(oldState);
        }
      }
    } catch (Throwable t) {
      throw new PrintingException("Something went wrong trying to append to buffer", t);
    }
  }

}
