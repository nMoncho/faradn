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
 * StarPRNT line-spacing and dot-feed commands for the TSP143IV. Unlike ESC/POS
 * ({@code ESC 3 n}), StarPRNT has <strong>no command to set the line pitch to
 * an
 * arbitrary number of dots</strong>: {@code ESC z n} only <em>selects</em> a
 * coarse pitch ({@code n=0/48} → 3&nbsp;mm/24 dots, {@code n=1/49} →
 * 4&nbsp;mm/32
 * dots) and {@code ESC 0} fixes it to 3&nbsp;mm. So an arbitrary
 * {@code line-height} is emulated in the renderer by leaving the pitch alone
 * and
 * adding a one-time dot feed after each line: {@code ESC I n} (n × 1 dot) or
 * {@code ESC J n} (n × 2 dots) at 203&nbsp;dpi. All Spec.&nbsp;1 on this model.
 * Verified against the StarPRNT Command Specifications (Rev 4.20), pp46-48.
 */
public final class StarLineSpacingCommands {

  /**
   * {@code ESC z n} - select the line pitch: {@code n=0} → 3&nbsp;mm, {@code n=1}
   * → 4&nbsp;mm (only).
   */
  public static final ParametricCode<Amount> SELECT_LINE_PITCH = new ParametricCode<>(
      new byte[] { Code.ESC, 0x7A });

  /** {@code ESC 0} - set the line pitch to 3&nbsp;mm (24 dots). */
  public static final Code LINE_PITCH_3MM = new SimpleCode("ESC 0", new byte[] { Code.ESC, 0x30 });

  /** {@code ESC J n} - print and feed the paper {@code n × 2} dots (one-time). */
  public static final ParametricCode<Amount> PRINT_AND_FEED_DOTS = new ParametricCode<>(
      new byte[] { Code.ESC, 0x4A });

  /** {@code ESC I n} - print and feed the paper {@code n × 1} dot (one-time). */
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
