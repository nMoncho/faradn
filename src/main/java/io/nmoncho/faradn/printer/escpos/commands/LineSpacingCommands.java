package io.nmoncho.faradn.printer.escpos.commands;

import io.nmoncho.faradn.printer.escpos.Code;
import io.nmoncho.faradn.printer.escpos.ParametricCode;
import io.nmoncho.faradn.printer.escpos.SimpleCode;
import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit;

public class LineSpacingCommands {

  public static Code DEFAULT_LINE_SPACING = new SimpleCode(new byte[] { Code.ESC, 0x32 });
  public static ParametricCode<MotionUnit> SET_LINE_SPACING = new ParametricCode<>(new byte[] { Code.ESC, 0x33 });

}
