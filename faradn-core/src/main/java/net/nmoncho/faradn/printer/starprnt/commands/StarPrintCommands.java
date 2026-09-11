//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import net.nmoncho.faradn.printer.command.Byteable;
import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.ParametricCode;
import net.nmoncho.faradn.printer.command.SimpleCode;

/**
 * StarPRNT print / paper-feed commands for the TSP143IV. {@code LF} is shared
 * with ESC/POS, but feed-n-lines is {@code ESC a n} - which is
 * <em>alignment</em>
 * in ESC/POS - so it must replace, never reuse, the ESC/POS constant. Verified
 * against the StarPRNT Command Specifications (Rev 4.20).
 */
public final class StarPrintCommands {

  /** {@code LF} (0x0A) - print the line buffer and feed one line. */
  public static final Code LINE_FEED = new SimpleCode("LF", 0x0A);

  /** {@code ESC a n} - print and feed {@code n} lines ({@code 1..127}). */
  public static final ParametricCode<Lines> PRINT_AND_FEED_LINES = new ParametricCode<>(
      new byte[] { Code.ESC, 0x61 });

  private StarPrintCommands() {
  }

  /** Parameter for {@code ESC a}: a line count in {@code [1, 127]}. */
  public static final class Lines implements Byteable {
    private final int lines;

    public Lines(int lines) {
      if (lines < 1 || lines > 127) {
        throw new IllegalArgumentException("lines must be in [1, 127], got " + lines);
      }
      this.lines = lines;
    }

    public static Lines of(int lines) {
      return new Lines(lines);
    }

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) lines };
    }
  }
}
