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
}
