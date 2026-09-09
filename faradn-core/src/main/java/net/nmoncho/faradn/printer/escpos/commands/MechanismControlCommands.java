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

  // ESC p m t1 t2 - pulse the drawer-kick connector: m selects the pin (0 -> pin
  // 2, 1 -> pin 5), t1/t2 are the ON/OFF pulse widths in units of 2 ms. 50 ms on
  // is a safe, widely used value that keeps the solenoid from over-energizing.
  private static final byte PULSE_ON = 25; // 50 ms
  private static final byte PULSE_OFF = (byte) 250; // 500 ms

  /** {@code ESC p 0 t1 t2} - pulse the drawer-kick connector on pin 2. */
  public static Code DRAWER_KICK_PIN_2 = new SimpleCode("ESC p",
      new byte[] { Code.ESC, 0x70, 0x00, PULSE_ON, PULSE_OFF });

  /** {@code ESC p 1 t1 t2} - pulse the drawer-kick connector on pin 5. */
  public static Code DRAWER_KICK_PIN_5 = new SimpleCode("ESC p",
      new byte[] { Code.ESC, 0x70, 0x01, PULSE_ON, PULSE_OFF });

}
