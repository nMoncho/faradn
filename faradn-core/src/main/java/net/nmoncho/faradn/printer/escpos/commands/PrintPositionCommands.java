//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos.commands;

import net.nmoncho.faradn.printer.escpos.Byteable;
import net.nmoncho.faradn.printer.escpos.Code;
import net.nmoncho.faradn.printer.escpos.ParametricCode;
import net.nmoncho.faradn.printer.escpos.SimpleCode;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit2D;

public class PrintPositionCommands {

  public static Code HORIZONTAL_TAB = new SimpleCode("HT", 0x09);

  // Horizontal print position (valid in both standard and page mode).
  public static ParametricCode<Word16> SET_ABSOLUTE_PRINT_POSITION = new ParametricCode<>(
      new byte[] { Code.ESC, 0x24 }); // ESC $
  public static ParametricCode<Word16> SET_RELATIVE_PRINT_POSITION = new ParametricCode<>(
      new byte[] { Code.ESC, 0x5C }); // ESC '\'

  // Vertical print position (page mode only).
  public static ParametricCode<Word16> SET_ABSOLUTE_VERTICAL_PRINT_POSITION = new ParametricCode<>(
      new byte[] { Code.GS, 0x24 }); // GS $
  public static ParametricCode<Word16> SET_RELATIVE_VERTICAL_PRINT_POSITION = new ParametricCode<>(
      new byte[] { Code.GS, 0x5C }); // GS '\'

  // Page-mode print area and direction.
  public static ParametricCode<PrintArea> SET_PRINT_AREA = new ParametricCode<>(new byte[] { Code.ESC, 0x57 }); // ESC W
  public static ParametricCode<Direction> SELECT_PRINT_DIRECTION = new ParametricCode<>(
      new byte[] { Code.ESC, 0x54 }); // ESC T

  public static ParametricCode<Justification> SELECT_JUSTIFICATION = new ParametricCode<>(
      new byte[] { Code.ESC, 0x61 }); // ESC a

  // TODO ESC D Set horizontal tab positions
  public static ParametricCode<MotionUnit2D> SET_LEFT_MARGIN = new ParametricCode<>(new byte[] { Code.GS, 0x4C }); // GS L
  public static ParametricCode<MotionUnit2D> SET_PRINT_AREA_WIDTH = new ParametricCode<>(
      new byte[] { Code.GS, 0x57 }); // GS W
  // GS P: set the horizontal/vertical motion units to 1/x" and 1/y" (x, y as the two params).
  public static ParametricCode<MotionUnit2D> SET_MOTION_UNITS = new ParametricCode<>(new byte[] { Code.GS, 0x50 });

  public enum Justification implements Byteable {
    LEFT, CENTER, RIGHT;

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) this.ordinal() };
    }
  }

  /**
   * Print direction in page mode ({@code ESC T n}): the reading direction and
   * origin corner, which together rotate content by 0/90/180/270°.
   */
  public enum Direction implements Byteable {
    /** {@code n=0}: left→right, origin top-left (no rotation). */
    LEFT_TO_RIGHT,
    /** {@code n=1}: bottom→top, origin bottom-left (90° counter-clockwise). */
    BOTTOM_TO_TOP,
    /** {@code n=2}: right→left, origin bottom-right (180°). */
    RIGHT_TO_LEFT,
    /** {@code n=3}: top→bottom, origin top-right (90° clockwise). */
    TOP_TO_BOTTOM;

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) this.ordinal() };
    }
  }

  /**
   * A 16-bit value encoded little-endian as {@code nL nH}
   * ({@code n = nL + nH×256}),
   * the position argument shared by the ESC/POS position commands. Absolute
   * positions are 0-65535; relative positions are signed (-32768 to 32767) and
   * encode as two's complement.
   */
  public static class Word16 implements Byteable {
    private final int value;

    public Word16(int value) {
      if (value < -32768 || value > 65535) {
        throw new IllegalArgumentException("value must be in [-32768, 65535], got " + value);
      }
      this.value = value;
    }

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF) };
    }
  }

  /**
   * The page-mode print area for {@code ESC W}: origin {@code (x, y)} and size
   * {@code (dx, dy)} in motion units, each a 16-bit little-endian value.
   */
  public static class PrintArea implements Byteable {
    private final int x;
    private final int y;
    private final int dx;
    private final int dy;

    public PrintArea(int x, int y, int dx, int dy) {
      this.x = checked(x, "x");
      this.y = checked(y, "y");
      this.dx = checked(dx, "dx");
      this.dy = checked(dy, "dy");
    }

    private static int checked(int value, String name) {
      if (value < 0 || value > 65535) {
        throw new IllegalArgumentException(name + " must be in [0, 65535], got " + value);
      }
      return value;
    }

    @Override
    public byte[] getBytes() {
      return new byte[] {
          (byte) (x & 0xFF), (byte) ((x >> 8) & 0xFF),
          (byte) (y & 0xFF), (byte) ((y >> 8) & 0xFF),
          (byte) (dx & 0xFF), (byte) ((dx >> 8) & 0xFF),
          (byte) (dy & 0xFF), (byte) ((dy >> 8) & 0xFF),
      };
    }
  }
}
