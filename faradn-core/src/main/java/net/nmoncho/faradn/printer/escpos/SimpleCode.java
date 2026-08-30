package net.nmoncho.faradn.printer.escpos;

/**
 * {@link net.nmoncho.faradn.printer.escpos.Code} implementation for codes that
 * don't need any special parameters.
 */
public class SimpleCode implements Code {

  private final String humanReadableCode;
  private final byte[] code;

  public SimpleCode(byte[] code) {
    this(null, code);
  }

  public SimpleCode(String humanReadableCode, byte[] code) {
    this.humanReadableCode = humanReadableCode;
    this.code = code;
  }

  public SimpleCode(int code) {
    this(null, code);
  }

  public SimpleCode(String humanReadableCode, int code) {
    this.humanReadableCode = humanReadableCode;
    this.code = new byte[] { (byte) code };
  }

  @Override
  public byte[] getCode() {
    return this.code;
  }

  @Override
  public String toString() {
    return humanReadableCode != null ? humanReadableCode : humanReadable();
  }
}
