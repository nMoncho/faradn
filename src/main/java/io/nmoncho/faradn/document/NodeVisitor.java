package io.nmoncho.faradn.document;

import io.nmoncho.faradn.Document;
import io.nmoncho.faradn.PrintingException;
import io.nmoncho.faradn.document.PrintJobStates;
import io.nmoncho.faradn.document.PrintJobStates.State;
import io.nmoncho.faradn.printer.PrintJobState;
import org.javatuples.Pair;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.util.*;

public class NodeVisitor implements org.jsoup.select.NodeVisitor {

  private final Document doc;
  private final List<State> states;
  private final Queue<Pair<Node, PrintJobStates.StyleState>> styleStateByNode;
  private PrintJobStates.StyleState currentStyle;

  public NodeVisitor(Document doc) {
    this.doc = doc;
    this.states = new LinkedList<>();
    this.styleStateByNode = new LinkedList<>();
    this.currentStyle = PrintJobStates.initialStyle;
  }

  public List<State> states() {
    // FIXME NOT thread safe!
    doc.getDoc().body().traverse(this);
    return states;
  }

  @Override
  public void head(Node node, int depth) {
    try {
      final State state = states.isEmpty() ? currentStyle : states.get(states.size() - 1);
      final State newState = state.process(node);

      if (!newState.equals(state) || states.isEmpty()) {
        states.add(newState);
      }

      // Adding style change, so we can rollback to previous style later
      if (newState instanceof PrintJobStates.StyleState) {
        styleStateByNode.add(Pair.with(node, (PrintJobStates.StyleState) newState));
      }
    } catch (Throwable t) {
      throw new PrintingException("Something went wrong trying traverse the document on head", t);
    }
  }

  @Override
  public void tail(Node node, int depth) {
    try {
      // Find previous state to rollback to, if applies
      rollbackStyle(node).ifPresent(state -> {
        currentStyle = state;
        states.add(state);
      });
    } catch (Throwable t) {
      throw new PrintingException("Something went wrong trying to append to buffer", t);
    }
  }

  private Optional<PrintJobStates.StyleState> rollbackStyle(Node node) {
    return Optional
      .ofNullable(styleStateByNode.peek())
      .flatMap(lastStyleChange -> {
        if (node instanceof Element && lastStyleChange.getValue0().equals(node)) {
          styleStateByNode.remove();

          return Optional
            .ofNullable(styleStateByNode.peek())
            .map(Pair::getValue1);
        } else {
          return Optional.empty();
        }
      });
  }

}
