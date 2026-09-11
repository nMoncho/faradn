//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import net.nmoncho.faradn.printer.command.Byteable;
import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.ParametricCode;

/**
 * StarPRNT print-position commands for the TSP143IV. The receipt flow only
 * needs
 * justification: {@code ESC GS a n}, which carries an extra leading {@code GS}
 * versus ESC/POS {@code ESC a n}. Verified against the StarPRNT Command
 * Specifications (Rev 4.20), p45.
 */
public final class StarPrintPositionCommands {

  /**
   * {@code ESC GS a n} - align {@code n=0}/{@code 1}/{@code 2} =
   * left/center/right.
   */
  public static final ParametricCode<Justification> SELECT_JUSTIFICATION = new ParametricCode<>(
      new byte[] { Code.ESC, Code.GS, 0x61 });

  private StarPrintPositionCommands() {
  }

  /** Line justification; the ordinal is the {@code ESC GS a} parameter. */
  public enum Justification implements Byteable {
    LEFT, CENTER, RIGHT;

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) this.ordinal() };
    }
  }
}
