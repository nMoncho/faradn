package net.nmoncho.faradn.printer.escpos.commands;

import net.nmoncho.faradn.printer.escpos.Code;
import net.nmoncho.faradn.printer.escpos.ParametricCode;
import net.nmoncho.faradn.printer.escpos.SimpleCode;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.Lines;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit;

public class PrintCommands {

  public static Code LINE_FEED = new SimpleCode("LF", 0x0A);
  public static Code PRINT_AND_GOTO_STANDARD = new SimpleCode("FF", 0x0C);
  public static Code CARRIAGE_RETURN = new SimpleCode("CR", 0x0D);
  public static Code PRINT_IN_PAGE_MODE = new SimpleCode("ESC FF", new byte[] { Code.ESC, 0x0C });

  // TODO for some printers, MU is defined with 'GS P'
  public static ParametricCode<MotionUnit> PRINT_AND_FEED_PAPER = new ParametricCode<>(new byte[] { Code.ESC, 0x4A });

  // TODO for some printer, This command must not be executed consecutively more than two times
  // Investigate how to constraint this
  public static ParametricCode<MotionUnit> PRINT_AND_REVERSE_FEED = new ParametricCode<>(
      new byte[] { Code.ESC, 0x4B });

  public static ParametricCode<Lines> PRINT_AND_FEED_LINES = new ParametricCode<>(new byte[] { Code.ESC, 0x64 });
  public static ParametricCode<Lines> PRINT_AND_REVERSE_LINES = new ParametricCode<>(new byte[] { Code.ESC, 0x65 });

}
