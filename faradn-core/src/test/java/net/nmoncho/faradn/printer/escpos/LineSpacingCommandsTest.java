//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos;

import static net.nmoncho.faradn.printer.escpos.commands.LineSpacingCommands.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class LineSpacingCommandsTest {

  @Test
  void printHumanReadableTest() {
    assertEquals("ESC 2", DEFAULT_LINE_SPACING.toString());
    assertEquals("ESC 3", SET_LINE_SPACING.toString());
  }

}
