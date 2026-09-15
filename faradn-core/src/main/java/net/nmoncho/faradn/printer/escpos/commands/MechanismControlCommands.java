//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos.commands;

import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.SimpleCode;

/**
 * ESC/POS "Mechanism control commands".
 */
public class MechanismControlCommands {

  /** {@code GS V 0} - full cut. */
  public static final Code FULL_CUT = new SimpleCode("GS V", new byte[] { Code.GS, 0x56, 0x00 });

  /**
   * {@code GS V 1} - partial cut leaving one point (bridge) uncut, so the receipt
   * stays attached. Equivalent to the legacy {@code ESC i}.
   */
  public static final Code PARTIAL_CUT = new SimpleCode("GS V", new byte[] { Code.GS, 0x56, 0x01 });

  /**
   * {@code ESC m} - partial cut leaving three points (bridges) uncut. There is no
   * {@code GS V} equivalent for a three-point cut, so this legacy command is the
   * only way to select it.
   */
  public static final Code PARTIAL_CUT_THREE_POINT = new SimpleCode("ESC m", new byte[] { Code.ESC, 0x6D });

  // ESC p m t1 t2 - pulse the drawer-kick connector: m selects the pin (0 -> pin
  // 2, 1 -> pin 5), t1/t2 are the ON/OFF pulse widths in units of 2 ms. 50 ms on
  // is a safe, widely used value that keeps the solenoid from over-energizing.
  private static final byte PULSE_ON = 25; // 50 ms
  private static final byte PULSE_OFF = (byte) 250; // 500 ms

  /** {@code ESC p 0 t1 t2} - pulse the drawer-kick connector on pin 2. */
  public static final Code DRAWER_KICK_PIN_2 = new SimpleCode("ESC p",
      new byte[] { Code.ESC, 0x70, 0x00, PULSE_ON, PULSE_OFF });

  /** {@code ESC p 1 t1 t2} - pulse the drawer-kick connector on pin 5. */
  public static final Code DRAWER_KICK_PIN_5 = new SimpleCode("ESC p",
      new byte[] { Code.ESC, 0x70, 0x01, PULSE_ON, PULSE_OFF });

}
