//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import net.nmoncho.faradn.printer.command.Byteable.ByteByteable;
import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.ParametricCode;
import net.nmoncho.faradn.printer.command.SimpleCode;

/**
 * StarPRNT status commands for the TSP143IV. StarPRNT has no ESC/POS
 * {@code DLE EOT} real-time poll; instead it uses Automatic Status Back (ASB),
 * which is push-model but can also be polled synchronously with
 * {@code ESC ACK SOH}. The printer answers with an ASB status block (decoded by
 * {@link net.nmoncho.faradn.transport.PrinterStatus#ofStarAsb(byte[])}).
 * Verified against the StarPRNT Command Specifications (Rev 4.20), p106 and the
 * Automatic Status appendix pp217-233.
 */
public final class StarStatusCommands {

  /** {@code ESC ACK SOH} - poll the current ASB status block on demand. */
  public static final Code POLL_ASB = new SimpleCode("ESC ACK SOH", new byte[] { Code.ESC, 0x06, 0x01 });

  /**
   * {@code ESC RS a n} - enable ({@code n=1}) / disable ({@code n=0}) push-model
   * ASB.
   */
  public static final ParametricCode<AsbMode> SET_ASB = new ParametricCode<>(
      new byte[] { Code.ESC, 0x1E, 0x61 });

  private StarStatusCommands() {
  }

  /**
   * Parameter for {@code ESC RS a}: the ASB enable flag ({@code 0}/{@code 1}).
   */
  public static final class AsbMode extends ByteByteable {
    public AsbMode(int mode) {
      super(mode);
    }
  }
}
