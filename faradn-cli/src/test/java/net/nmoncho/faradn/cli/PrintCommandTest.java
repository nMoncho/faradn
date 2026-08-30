package net.nmoncho.faradn.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

public class PrintCommandTest {

  private static final String RECEIPT = "../faradn-core/src/test/resources/printjobs/receipt-text.html";

  @Test
  void dryRunWritesEscPosBytesToStdout() {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream original = System.out;
    System.setOut(new PrintStream(captured));

    int exit;
    try {
      exit = new CommandLine(new Faradn()).execute("print", "--dry-run", RECEIPT);
    } finally {
      System.setOut(original);
    }

    assertEquals(0, exit);
    byte[] bytes = captured.toByteArray();
    assertTrue(bytes.length > 10);
    assertEquals(0x1B, bytes[0] & 0xFF); // ESC
    assertEquals(0x40, bytes[1] & 0xFF); // @ (initialize)
  }

  @Test
  void printWithoutATargetFails() {
    int exit = new CommandLine(new Faradn()).execute("print", RECEIPT);

    assertEquals(2, exit); // no --printer / --host / --dry-run
  }

  @Test
  void unknownProfileFails() {
    int exit = new CommandLine(new Faradn()).execute("print", "--dry-run", "--profile", "nope", RECEIPT);

    assertEquals(2, exit);
  }
}
