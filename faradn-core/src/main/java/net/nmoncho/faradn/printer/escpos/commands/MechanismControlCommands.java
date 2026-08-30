package net.nmoncho.faradn.printer.escpos.commands;

import net.nmoncho.faradn.printer.escpos.Code;
import net.nmoncho.faradn.printer.escpos.SimpleCode;

/**
 * ESC/POS "Mechanism control commands".
 */
public class MechanismControlCommands {

  /** {@code GS V 0} - full cut. */
  public static Code FULL_CUT = new SimpleCode("GS V", new byte[] { Code.GS, 0x56, 0x00 });

  /**
   * {@code GS V 1} - partial cut, leaving a small bridge so the receipt stays
   * attached.
   */
  public static Code PARTIAL_CUT = new SimpleCode("GS V", new byte[] { Code.GS, 0x56, 0x01 });

}
