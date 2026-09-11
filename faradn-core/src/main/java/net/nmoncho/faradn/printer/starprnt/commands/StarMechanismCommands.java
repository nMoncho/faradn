//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.SimpleCode;

/**
 * StarPRNT mechanism commands for the TSP143IV: cutter and cash-drawer.
 * <ul>
 * <li><b>Cut</b> is {@code ESC d n} ({@code n=0} full, {@code n=1} partial) -
 * where ESC/POS {@code ESC d} is a line feed; a single-cutter model
 * auto-substitutes the other cut.</li>
 * <li><b>Drawer</b> is two-step: {@code ESC BEL n1 n2} <em>programs</em> the
 * pulse (energize/delay in 10&nbsp;ms units) but does not fire; a following
 * {@code BEL} drives drawer&nbsp;1 and {@code SUB} drives drawer&nbsp;2. These
 * constants bundle a program-then-fire pair. Farad'n's 50&nbsp;ms/500&nbsp;ms
 * pulse becomes {@code n1=5}, {@code n2=50}.</li>
 * </ul>
 * Verified against the StarPRNT Command Specifications (Rev 4.20), p52 and
 * pp136-138.
 */
public final class StarMechanismCommands {

  /** {@code ESC d 0} - full cut. */
  public static final Code FULL_CUT = new SimpleCode("ESC d", new byte[] { Code.ESC, 0x64, 0x00 });

  /**
   * {@code ESC d 1} - partial cut, leaving a bridge so the receipt stays
   * attached.
   */
  public static final Code PARTIAL_CUT = new SimpleCode("ESC d", new byte[] { Code.ESC, 0x64, 0x01 });

  // ESC BEL n1 n2 programs the drawer pulse in 10 ms units; 50 ms on / 500 ms off
  // matches the ESC/POS defaults and keeps the solenoid from over-energizing.
  private static final byte PULSE_ON = 5; // 50 ms
  private static final byte PULSE_OFF = 50; // 500 ms

  /**
   * Program the pulse ({@code ESC BEL n1 n2}) then fire drawer&nbsp;1
   * ({@code BEL}).
   */
  public static final Code DRAWER_KICK_1 = new SimpleCode("ESC BEL",
      new byte[] { Code.ESC, 0x07, PULSE_ON, PULSE_OFF, 0x07 });

  /**
   * Program the pulse ({@code ESC BEL n1 n2}) then fire drawer&nbsp;2
   * ({@code SUB}).
   */
  public static final Code DRAWER_KICK_2 = new SimpleCode("ESC BEL",
      new byte[] { Code.ESC, 0x07, PULSE_ON, PULSE_OFF, 0x1A });

  private StarMechanismCommands() {
  }
}
