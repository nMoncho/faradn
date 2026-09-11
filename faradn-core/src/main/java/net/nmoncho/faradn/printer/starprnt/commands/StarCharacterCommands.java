//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import net.nmoncho.faradn.printer.command.BooleanCode;
import net.nmoncho.faradn.printer.command.Byteable;
import net.nmoncho.faradn.printer.command.Byteable.ByteByteable;
import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.ParametricCode;
import net.nmoncho.faradn.printer.command.SimpleCode;

/**
 * StarPRNT character-style commands for the TSP143IV, verified against the
 * StarPRNT Command Specifications (Rev 4.20). Several opcodes differ from
 * ESC/POS in shape, so this is a deliberate parallel dictionary, not a reuse:
 * <ul>
 * <li><b>bold</b> is two opcodes with <em>no</em> parameter byte
 * ({@code ESC E} on / {@code ESC F} off), unlike ESC/POS {@code ESC E n};</li>
 * <li><b>invert</b> ({@code ESC 4}/{@code ESC 5}) collides byte-for-byte with
 * ESC/POS italic ({@code SELECT_ITALIC}/{@code CANCEL_ITALIC}) - these
 * constants
 * must be used, never the ESC/POS ones;</li>
 * <li><b>italic</b> has no StarPRNT opcode at all, so the renderer no-ops
 * it;</li>
 * <li><b>size</b> is {@code ESC i n1 n2} (height then width, each
 * {@code multiplier - 1}, max 6x on this model), not the nibble-packed
 * {@code GS !};</li>
 * <li><b>font</b> is {@code ESC RS F n} (Font-A/Font-B), not
 * {@code ESC M}.</li>
 * </ul>
 */
public final class StarCharacterCommands {

  /** {@code RS} (0x1E): the middle byte of {@code ESC RS F}. */
  private static final byte RS = 0x1E;

  /** {@code ESC E} - turn bold on (no parameter byte). */
  public static final Code BOLD_ON = new SimpleCode("ESC E", new byte[] { Code.ESC, 0x45 });
  /** {@code ESC F} - turn bold off (no parameter byte). */
  public static final Code BOLD_OFF = new SimpleCode("ESC F", new byte[] { Code.ESC, 0x46 });

  /** {@code ESC - n} - underline on ({@code n=1}) / off ({@code n=0}). */
  public static final BooleanCode UNDERLINE = new BooleanCode(new byte[] { Code.ESC, 0x2D });

  // Invert / highlight (white-on-black). NOTE: 1B 34 / 1B 35 are italic in
  // Farad'n's ESC/POS layer; on Star they mean invert. Keep them separate.
  /** {@code ESC 4} - turn white/black reverse (invert) on. */
  public static final Code INVERT_ON = new SimpleCode("ESC 4", new byte[] { Code.ESC, 0x34 });
  /** {@code ESC 5} - turn white/black reverse (invert) off. */
  public static final Code INVERT_OFF = new SimpleCode("ESC 5", new byte[] { Code.ESC, 0x35 });

  // Upside-down: bare control bytes, no parameter (ESC/POS uses ESC { n). Only
  // toggles at the top of a line; the renderer hoists it to line start.
  /** {@code SI} (0x0F) - turn upside-down printing on. */
  public static final Code UPSIDE_DOWN_ON = new SimpleCode("SI", 0x0F);
  /** {@code DC2} (0x12) - turn upside-down printing off. */
  public static final Code UPSIDE_DOWN_OFF = new SimpleCode("DC2", 0x12);

  /**
   * {@code ESC i n1 n2} - character magnification ({@code n1}=height,
   * {@code n2}=width).
   */
  public static final ParametricCode<StarCharacterSize> SELECT_CHARACTER_SIZE = new ParametricCode<>(
      new byte[] { Code.ESC, 0x69 });

  /**
   * {@code ESC RS F n} - select font ({@code n=0} Font-A, {@code n=1} Font-B).
   */
  public static final ParametricCode<FontSlot> SELECT_FONT = new ParametricCode<>(
      new byte[] { Code.ESC, RS, 0x46 });

  private StarCharacterCommands() {
  }

  /**
   * Parameter for {@code ESC i}: width and height magnification, each an integer
   * multiple from 1x to 6x (the TSP143IV's {@code ESC i} Spec.&nbsp;1 limit). The
   * two parameter bytes are {@code n1 = height - 1} then {@code n2 = width - 1}.
   */
  public static final class StarCharacterSize implements Byteable {
    private final int width;
    private final int height;

    public StarCharacterSize(int width, int height) {
      if (width < 1 || width > 6) {
        throw new IllegalArgumentException("width must be in [1, 6], got " + width);
      }
      if (height < 1 || height > 6) {
        throw new IllegalArgumentException("height must be in [1, 6], got " + height);
      }
      this.width = width;
      this.height = height;
    }

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) (height - 1), (byte) (width - 1) }; // n1 = height, n2 = width
    }
  }

  /**
   * Parameter for {@code ESC RS F}: the font slot ({@code 0} Font-A, {@code 1}
   * Font-B).
   */
  public static final class FontSlot extends ByteByteable {
    public FontSlot(int slot) {
      super(slot);
    }
  }
}
