package net.nmoncho.faradn.printer.escpos.commands;

import net.nmoncho.faradn.printer.escpos.Code;
import net.nmoncho.faradn.printer.escpos.SimpleCode;

/**
 * ESC/POS "Miscellaneous function commands".
 */
public class MiscellaneousCommands {

  /**
   * {@code ESC @} - initialize the printer: clears the buffer and resets modes.
   */
  public static Code INITIALIZE = new SimpleCode("ESC @", new byte[] { Code.ESC, 0x40 });

}
