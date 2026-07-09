package io.nmoncho.faradn.jobs;

import java.io.File;
import java.util.List;

import io.nmoncho.faradn.document.NodeVisitor;
import io.nmoncho.faradn.document.PrintJobStates;
import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.Document;

public class ParagraphJobTest {

  @Test
  void simplePrintJob() throws Exception {
    File f = new File("src/test/resources/elementjobs/paragraph.html");
    Document doc = Document.from(f);
    System.out.println(doc);
    NodeVisitor visitor = new NodeVisitor(doc);

    List<PrintJobStates.State> states = visitor.states();
    System.out.println("==========================================");
    for (PrintJobStates.State state : states) {
      System.out.println(state);
    }
  }
}
