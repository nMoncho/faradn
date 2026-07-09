package io.nmoncho.faradn.printer.escpos;

import static io.nmoncho.faradn.printer.escpos.commands.LineSpacingCommands.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class LineSpacingCommandsTest {

  @Test
  void printHumanReadableTest() {
    assertEquals("ESC 2", DEFAULT_LINE_SPACING.toString());
    assertEquals("ESC 3", SET_LINE_SPACING.toString());
  }

}
