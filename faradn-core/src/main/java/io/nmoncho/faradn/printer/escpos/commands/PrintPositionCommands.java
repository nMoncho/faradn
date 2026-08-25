package io.nmoncho.faradn.printer.escpos.commands;

import io.nmoncho.faradn.printer.escpos.Byteable;
import io.nmoncho.faradn.printer.escpos.Code;
import io.nmoncho.faradn.printer.escpos.ParametricCode;
import io.nmoncho.faradn.printer.escpos.SimpleCode;
import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit2D;

public class PrintPositionCommands {

  public static Code HORIZONTAL_TAB = new SimpleCode("HT", 0x09);
  public static ParametricCode<MotionUnit2D> SET_ABSOLUTE_PRINT_POSITION = new ParametricCode<>(
      new byte[] { Code.ESC, 0x24 });
  // TODO ESC D Set horizontal tab positions
  // TODO ESC T Select print direction in page mode
  // TODO ESC W Set print area in page mode
  // TODO ESC \ Set relative print position
  public static ParametricCode<Justification> SELECT_JUSTIFICATION = new ParametricCode<>(
      new byte[] { Code.ESC, 0x61 });
  // TODO GS $ Set absolute vertical print position in page mode
  public static ParametricCode<MotionUnit2D> SET_LEFT_MARGIN = new ParametricCode<>(new byte[] { Code.GS, 0x4C });
  public static ParametricCode<MotionUnit2D> SET_PRINT_AREA_WIDTH = new ParametricCode<>(
      new byte[] { Code.GS, 0x57 });
  // TODO GS \ Set relative vertical print position in page mode

  public enum Justification implements Byteable {
    LEFT, CENTER, RIGHT;

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) this.ordinal() };
    }
  }
}
