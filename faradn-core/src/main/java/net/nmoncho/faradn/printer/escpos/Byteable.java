package net.nmoncho.faradn.printer.escpos;

public interface Byteable {

  enum Boolean implements Byteable {
    ON, OFF;

    private static final byte[] off = new byte[] { 0x00 };
    private static final byte[] on = new byte[] { 0x01 };

    public byte[] getBytes() {
      return this == ON ? on : off;
    }
  }

  abstract class ByteByteable implements Byteable {
    private final byte b;

    public ByteByteable(byte b) {
      this.b = b;
    }

    public ByteByteable(int i) {
      this.b = (byte) i;
    }

    public byte[] getBytes() {
      return new byte[] { b };
    }
  }

  byte[] getBytes();
}
