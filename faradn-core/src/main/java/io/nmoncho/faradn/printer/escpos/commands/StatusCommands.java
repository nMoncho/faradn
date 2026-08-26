package io.nmoncho.faradn.printer.escpos.commands;

import io.nmoncho.faradn.printer.escpos.Code;
import io.nmoncho.faradn.printer.escpos.SimpleCode;

/**
 * ESC/POS real-time status commands ({@code DLE EOT n}). The printer answers
 * each with a single status byte, even while it is printing.
 */
public class StatusCommands {

  /** {@code DLE EOT 1} - printer status (online/offline, drawer). */
  public static Code PRINTER_STATUS = new SimpleCode("DLE EOT 1", new byte[] { 0x10, 0x04, 0x01 });

  /**
   * {@code DLE EOT 2} - offline cause (cover open, paper fed by button, paper-end
   * stop).
   */
  public static Code OFFLINE_STATUS = new SimpleCode("DLE EOT 2", new byte[] { 0x10, 0x04, 0x02 });

  /**
   * {@code DLE EOT 3} - error cause (cutter, unrecoverable, auto-recoverable).
   */
  public static Code ERROR_STATUS = new SimpleCode("DLE EOT 3", new byte[] { 0x10, 0x04, 0x03 });

  /** {@code DLE EOT 4} - paper roll sensor (near-end, paper end). */
  public static Code PAPER_ROLL_STATUS = new SimpleCode("DLE EOT 4", new byte[] { 0x10, 0x04, 0x04 });

}
