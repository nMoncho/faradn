package io.nmoncho.faradn.printer.escpos.commands;

import io.nmoncho.faradn.printer.escpos.BooleanCode;
import io.nmoncho.faradn.printer.escpos.Byteable;
import io.nmoncho.faradn.printer.escpos.Byteable.ByteByteable;
import io.nmoncho.faradn.printer.escpos.Code;
import io.nmoncho.faradn.printer.escpos.ParametricCode;
import io.nmoncho.faradn.printer.escpos.SimpleCode;

public class CharacterCommands {

  public static Code CANCEL_PAGE_MODE = new SimpleCode("CAN", 0x18);

  public static ParametricCode<MotionUnit> SET_RIGHT_SIDE_SPACING = new ParametricCode<>(
      new byte[] { Code.ESC, 0x20 });

  public static ParametricCode<PrintMode> SELECT_PRINT_MODE = new ParametricCode<>(
      new byte[] { Code.ESC, 0x21 });

  public static BooleanCode CANCEL_USER_DEFINED_CHARACTERS = new BooleanCode(new byte[] { Code.ESC, 0x3F });
  public static BooleanCode USER_DEFINED_CHARACTER_SET = new BooleanCode(new byte[] { Code.ESC, 0x25 });
  // TODO understand following code: ESC & 'Define user-defined characters', SEE Page 107

  // TODO define this UNDERLINE with 2px thick, not only true/false
  public static BooleanCode UNDERLINE = new BooleanCode(new byte[] { Code.ESC, 0x2D });
  public static BooleanCode EMPHASIZED = new BooleanCode(new byte[] { Code.ESC, 0x45 });
  public static BooleanCode DOUBLE_STRIKE = new BooleanCode(new byte[] { Code.ESC, 0x47 });
  // TODO 'SELECT_FONT'
  // TODO 'SELECT_INTERNATIONAL_SET'
  public static BooleanCode TURN_UPSIDE = new BooleanCode(new byte[] { Code.ESC, 0x7B });
  public static BooleanCode REVERSE_BACKGROUND = new BooleanCode(new byte[] { Code.GS, 0x42 });
  public static BooleanCode SMOOTHING = new BooleanCode(new byte[] { Code.GS, 0x62 });

  // Cancel print data in page mode
  // Turn underline mode on/off
  // Select print mode(s)
  // Cancel user-defined characters
  // Turn upside-down print mode on/off
  // Define user-defined characters
  // Select/cancel user-defined character set
  // x Turn emphasized mode on/off
  // x Turn double-strike mode on/off
  // Select character font
  // Select print color
  // Select an international character set
  // x Set right-side character spacing
  // Select character code table
  // x Turn 90° clockwise rotation mode on/off
  // Select character size
  // Select character effects, Select character color, Select background color, Turn shading mode on/off
  // Turn white/black reverse print mode on/off
  // Turn smoothing mode on/off

  // If this class is used by multiple codes, then send up the hierarchy
  // TODO translation from MU to Inches should be done at the printer level since different models have different scales
  public static class MotionUnit extends ByteByteable {
    public MotionUnit(int units) {
      super(units);
    }
  }

  public static class MotionUnit2D implements Byteable {
    final int xUnits;
    final int yUnits;

    public MotionUnit2D(int xUnits, int yUnits) {
      this.xUnits = xUnits;
      this.yUnits = yUnits;
    }

    @Override
    public byte[] getBytes() {
      return new byte[] { (byte) xUnits, (byte) yUnits };
    }
  }

  public static class Lines extends ByteByteable {
    public Lines(int lines) {
      super(lines);
    }

    public static Lines of(int lines) {
      return new Lines(lines);
    }
  }

  public static class PrintMode implements Byteable {

    static int FONT_FLAG = 0x01;
    static int EMPHASIZED_FLAG = 0x08;
    static int DOUBLE_HEIGHT_FLAG = 0x10;
    static int DOUBLE_WIDTH_FLAG = 0x20;
    static int UNDERLINE_FLAG = 0x80;

    private final byte[] bytes;

    public PrintMode(byte[] bytes) {
      this.bytes = bytes;
    }

    public static PrintModeBuilder builder() {
      return new PrintModeBuilder();
    }

    @Override
    public byte[] getBytes() {
      return this.bytes;
    }

    public boolean isFont1() {
      return (bytes[0] & FONT_FLAG) == 0;
    }

    public boolean isFont2() {
      return (bytes[0] & FONT_FLAG) == 1;
    }

    public boolean isEmphasized() {
      return (bytes[0] & EMPHASIZED_FLAG) >= 1;
    }

    public boolean isDoubleHeight() {
      return (bytes[0] & DOUBLE_HEIGHT_FLAG) >= 1;
    }

    public boolean isDoubleWidth() {
      return (bytes[0] & DOUBLE_WIDTH_FLAG) >= 1;
    }

    public boolean isUnderline() {
      return (bytes[0] & UNDERLINE_FLAG) >= 1;
    }
  }

  // TODO check if this is Big Endianess or Little Endianess
  public static class PrintModeBuilder {

    private final int value;

    private PrintModeBuilder() {
      this(0);
    }

    private PrintModeBuilder(int value) {
      this.value = value;
    }

    public PrintMode build() {
      return new PrintMode(new byte[] { (byte) value });
    }

    private PrintModeBuilder withValue(int value) {
      return new PrintModeBuilder(value);
    }

    public PrintModeBuilder withCharacterFont1() {
      return withValue(value & (~PrintMode.FONT_FLAG));
    }

    public PrintModeBuilder withCharacterFont2() {
      return withValue(value | PrintMode.FONT_FLAG);
    }

    public PrintModeBuilder withEmphasized(boolean on) {
      return withValue(on ? value | PrintMode.EMPHASIZED_FLAG : value & (~PrintMode.EMPHASIZED_FLAG));
    }

    public PrintModeBuilder withDoubleHeight(boolean on) {
      return withValue(on ? value | PrintMode.DOUBLE_HEIGHT_FLAG : value & (~PrintMode.DOUBLE_HEIGHT_FLAG));
    }

    public PrintModeBuilder withDoubleWeight(boolean on) {
      return withValue(on ? value | PrintMode.DOUBLE_WIDTH_FLAG : value & (~PrintMode.DOUBLE_WIDTH_FLAG));
    }

    public PrintModeBuilder withUnderline(boolean on) {
      return withValue(on ? value | PrintMode.UNDERLINE_FLAG : value & (~PrintMode.UNDERLINE_FLAG));
    }
  }
}
