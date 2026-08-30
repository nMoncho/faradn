package net.nmoncho.faradn.printer.escpos;

public interface Code {

  byte[] NOOP = new byte[] {};

  char ESC = 0x1B;
  char GS = 0x1D;

  byte[] getCode();

  default String humanReadable() {
    byte[] code = getCode();
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < code.length; i++) {
      if (((char) code[i]) == ESC && i == 0) {
        sb.append("ESC ");
      } else if (((char) code[i]) == GS && i == 0) {
        sb.append("GS ");
      } else {
        sb.append((char) code[i]);
      }
    }

    return sb.toString();
  }

}
