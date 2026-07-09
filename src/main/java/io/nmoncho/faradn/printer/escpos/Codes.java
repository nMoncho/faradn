package io.nmoncho.faradn.printer.escpos;

public interface Codes {

  byte[] NOOP = new byte[] {};

  byte[] getCode();

  default boolean isToggable() {
    return false;
  }

  default String humanReadable() {
    byte[] code = getCode();
    int idx = 0;
    StringBuilder sb = new StringBuilder();

    if (code.length > 0 && code[idx] == ESC_CHAR) {
      sb.append("ESC ");
    } else if (code.length > 0 && code[idx] == GS) {
      sb.append("GS ");
    }

    for (int i = idx; i < code.length; i++) {
      sb.append((char) code[i]);
    }

    return sb.toString().replace("", "");
  }

  final static char ESC_CHAR = 0x1B;
  final static char GS = 0x1D;
  final static byte[] INIT_PRINTER = new byte[] { ESC_CHAR, 0x40 };
  final static byte[] SET_PRINT_MODE = new byte[] { ESC_CHAR, 0x21, 0x00 };
  final static byte[] INIT = { 27, 64 };
  final static byte[] CUT_PAPER = new byte[] { GS, 0x56, 0x00 };
  final static byte[] LINE_FEED = new byte[] { 0x0A };
  final static byte[] SELECT_FONT_A = { 27, 33, 0 };
  final static byte[] SET_BAR_CODE_HEIGHT = { 29, 104, 100 };
  final static byte[] PRINT_BAR_CODE_1 = { 29, 107, 2 };
  final static byte[] SEND_NULL_BYTE = { 0x00 };
  final static byte[] SELECT_PRINT_SHEET = { 0x1B, 0x63, 0x30, 0x02 };
  final static byte[] FEED_PAPER_AND_CUT = { 0x1D, 0x56, 66, 0x00 };
  final static byte[] SELECT_CYRILLIC_CHARACTER_CODE_TABLE = { 0x1B, 0x74, 0x11 };
  final static byte[] SELECT_BIT_IMAGE_MODE = { 0x1B, 0x2A, 33 };
  final static byte[] SET_LINE_SPACING_24 = { 0x1B, 0x33, 24 };
  final static byte[] SET_LINE_SPACING_30 = { 0x1B, 0x33, 30 };
  final static byte[] TRANSMIT_DLE_PRINTER_STATUS = { 0x10, 0x04, 0x01 };
  final static byte[] TRANSMIT_DLE_OFFLINE_PRINTER_STATUS = { 0x10, 0x04, 0x02 };
  final static byte[] TRANSMIT_DLE_ERROR_STATUS = { 0x10, 0x04, 0x03 };
  final static byte[] TRANSMIT_DLE_ROLL_PAPER_SENSOR_STATUS = { 0x10, 0x04, 0x04 };
  final static byte[] UNDERLINED_MODE = new byte[] { ESC_CHAR, 0x2D, 0x32 };
  final static byte[] EMPHASIZED_MODE_ON = new byte[] { ESC_CHAR, 0x45, 0x01 };
  final static byte[] EMPHASIZED_MODE_OFF = new byte[] { ESC_CHAR, 0x45, 0x00 };
  final static byte[] DSTRIKE_MODE_ON = new byte[] { ESC_CHAR, 0x47, 0x01 };
  final static byte[] DSTRIKE_MODE_OFF = new byte[] { ESC_CHAR, 0x47, 0x00 };
  final static byte[] ALIGN_LEFT = new byte[] { ESC_CHAR, 0x61, 0x30 };
  final static byte[] ALIGN_CENTER = new byte[] { ESC_CHAR, 0x61, 0x31 };
  final static byte[] ALIGN_RIGHT = new byte[] { ESC_CHAR, 0x61, 0x32 };
  final static byte[] UPSIDE_ON = new byte[] { ESC_CHAR, 0x7B, 0x01 };
  final static byte[] UPSIDE_OFF = new byte[] { ESC_CHAR, 0x7B, 0x00 };
  final static byte[] SET_LINE_SPACE_24 = new byte[] { ESC_CHAR, 0x33, 24 };
  final static byte[] SET_LINE_SPACE_30 = new byte[] { ESC_CHAR, 0x33, 30 };
  final static byte FONT_POINT = 0x11;

}
