package io.nmoncho.faradn.printer.escpos;

import static io.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class PrintPositionCommandsTest {

  @Test
  void printHumanReadableTest() {
    assertEquals("HT", HORIZONTAL_TAB.toString());
    assertEquals("ESC $", SET_ABSOLUTE_PRINT_POSITION.toString());

    assertEquals("ESC a", SELECT_JUSTIFICATION.toString());
    assertEquals("GS L", SET_LEFT_MARGIN.toString());
    assertEquals("GS W", SET_PRINT_AREA_WIDTH.toString());
  }

}
