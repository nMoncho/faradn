//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos.commands;

import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.SimpleCode;

/**
 * ESC/POS "Miscellaneous function commands".
 */
public class MiscellaneousCommands {

  /**
   * {@code ESC @} - initialize the printer: clears the buffer and resets modes.
   */
  public static final Code INITIALIZE = new SimpleCode("ESC @", new byte[] { Code.ESC, 0x40 });

}
