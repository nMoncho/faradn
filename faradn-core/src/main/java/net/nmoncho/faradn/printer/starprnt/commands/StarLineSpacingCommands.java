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
 * StarPRNT line-spacing and dot-feed commands for the TSP143IV, replacing
 * ESC/POS {@code ESC 3}/{@code ESC 2} (+ {@code GS P}). The line pitch is set
 * with {@code ESC z n} and reset with {@code ESC 0}; sub-line paper feeds use
 * {@code ESC J n} (n × 2 dots at 203&nbsp;dpi) and {@code ESC I n} (n × 1 dot).
 * All Spec.&nbsp;1 on this model. Verified against the StarPRNT Command
 * Specifications (Rev 4.20), pp46-48.
 */
public final class StarLineSpacingCommands {

  /** {@code ESC z n} - set the line pitch to {@code n} (0..255). */
  public static final ParametricCode<Amount> SET_LINE_SPACING = new ParametricCode<>(
      new byte[] { Code.ESC, 0x7A });

  /** {@code ESC 0} - restore the default line pitch. */
  public static final Code DEFAULT_LINE_SPACING = new SimpleCode("ESC 0", new byte[] { Code.ESC, 0x30 });

  /** {@code ESC J n} - print and feed the paper {@code n × 2} dots. */
  public static final ParametricCode<Amount> PRINT_AND_FEED_DOTS = new ParametricCode<>(
      new byte[] { Code.ESC, 0x4A });

  /** {@code ESC I n} - micro-feed the paper {@code n × 1} dot. */
  public static final ParametricCode<Amount> MICRO_FEED = new ParametricCode<>(
      new byte[] { Code.ESC, 0x49 });

  private StarLineSpacingCommands() {
  }

  /** A single-byte {@code n} amount (0..255) for the spacing/feed commands. */
  public static final class Amount extends ByteByteable {
    public Amount(int n) {
      super(n);
    }
  }
}
