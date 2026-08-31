package net.nmoncho.faradn.printer.escpos;

import static net.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit2D;

public class PrintPositionCommandsTest {

  @Test
  void printHumanReadableTest() {
    assertEquals("HT", HORIZONTAL_TAB.toString());
    assertEquals("ESC $", SET_ABSOLUTE_PRINT_POSITION.toString());
    assertEquals("ESC \\", SET_RELATIVE_PRINT_POSITION.toString());
    assertEquals("GS $", SET_ABSOLUTE_VERTICAL_PRINT_POSITION.toString());
    assertEquals("GS \\", SET_RELATIVE_VERTICAL_PRINT_POSITION.toString());
    assertEquals("ESC W", SET_PRINT_AREA.toString());
    assertEquals("ESC T", SELECT_PRINT_DIRECTION.toString());
    assertEquals("ESC a", SELECT_JUSTIFICATION.toString());
    assertEquals("GS L", SET_LEFT_MARGIN.toString());
    assertEquals("GS W", SET_PRINT_AREA_WIDTH.toString());
    assertEquals("GS P", SET_MOTION_UNITS.toString());
  }

  @Test
  void horizontalPositionBytes() {
    // 300 = 0x012C -> nL=0x2C, nH=0x01
    assertArrayEquals(new byte[] { 0x1B, 0x24, 0x2C, 0x01 }, SET_ABSOLUTE_PRINT_POSITION.getCode(new Word16(300)));
    assertArrayEquals(new byte[] { 0x1B, 0x5C, 0x2C, 0x01 }, SET_RELATIVE_PRINT_POSITION.getCode(new Word16(300)));
  }

  @Test
  void verticalPositionBytes() {
    assertArrayEquals(new byte[] { 0x1D, 0x24, 0x2C, 0x01 },
        SET_ABSOLUTE_VERTICAL_PRINT_POSITION.getCode(new Word16(300)));
    assertArrayEquals(new byte[] { 0x1D, 0x5C, 0x2C, 0x01 },
        SET_RELATIVE_VERTICAL_PRINT_POSITION.getCode(new Word16(300)));
  }

  @Test
  void printAreaBytes() {
    // ESC W with x=0, y=0, dx=512 (0x0200), dy=160 (0x00A0)
    assertArrayEquals(
        new byte[] { 0x1B, 0x57, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, (byte) 0xA0, 0x00 },
        SET_PRINT_AREA.getCode(new PrintArea(0, 0, 512, 160)));
  }

  @Test
  void printDirectionBytes() {
    assertArrayEquals(new byte[] { 0x1B, 0x54, 0x00 }, SELECT_PRINT_DIRECTION.getCode(Direction.LEFT_TO_RIGHT));
    assertArrayEquals(new byte[] { 0x1B, 0x54, 0x01 }, SELECT_PRINT_DIRECTION.getCode(Direction.BOTTOM_TO_TOP));
    assertArrayEquals(new byte[] { 0x1B, 0x54, 0x02 }, SELECT_PRINT_DIRECTION.getCode(Direction.RIGHT_TO_LEFT));
    assertArrayEquals(new byte[] { 0x1B, 0x54, 0x03 }, SELECT_PRINT_DIRECTION.getCode(Direction.TOP_TO_BOTTOM));
  }

  @Test
  void motionUnitsBytes() {
    assertArrayEquals(new byte[] { 0x1D, 0x50, (byte) 180, (byte) 180 },
        SET_MOTION_UNITS.getCode(new MotionUnit2D(180, 180)));
  }

  @Test
  void word16IsLittleEndianAndSigned() {
    assertArrayEquals(new byte[] { 0x00, 0x00 }, new Word16(0).getBytes());
    assertArrayEquals(new byte[] { 0x00, 0x01 }, new Word16(256).getBytes());
    assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xFF }, new Word16(65535).getBytes());
    // Relative positions are signed: -1 -> two's complement 0xFFFF.
    assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xFF }, new Word16(-1).getBytes());
  }

  @Test
  void argumentsAreRangeChecked() {
    assertThrows(IllegalArgumentException.class, () -> new Word16(65536));
    assertThrows(IllegalArgumentException.class, () -> new Word16(-32769));
    assertThrows(IllegalArgumentException.class, () -> new PrintArea(-1, 0, 10, 10));
    assertThrows(IllegalArgumentException.class, () -> new PrintArea(0, 0, 65536, 10));
  }
}
