package net.nmoncho.faradn.printer.escpos;

import static net.nmoncho.faradn.printer.escpos.commands.PrintCommands.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class PrintCommandsTest {

  @Test
  void printHumanReadableTest() {
    assertEquals("LF", LINE_FEED.toString());
    assertEquals("FF", PRINT_AND_GOTO_STANDARD.toString());
    assertEquals("CR", CARRIAGE_RETURN.toString());
    assertEquals("ESC FF", PRINT_IN_PAGE_MODE.toString());

    assertEquals("ESC J", PRINT_AND_FEED_PAPER.toString());
    assertEquals("ESC K", PRINT_AND_REVERSE_FEED.toString());

    assertEquals("ESC d", PRINT_AND_FEED_LINES.toString());
    assertEquals("ESC e", PRINT_AND_REVERSE_LINES.toString());
  }

}
