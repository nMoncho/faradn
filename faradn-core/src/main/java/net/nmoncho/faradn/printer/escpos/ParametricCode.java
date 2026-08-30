package net.nmoncho.faradn.printer.escpos;

public class ParametricCode<T extends Byteable> implements Code {

  private final byte[] code;

  public ParametricCode(byte[] code) {
    this.code = code;
  }

  public byte[] getCode(T t) {
    byte[] code = getCode();
    byte[] params = t.getBytes();

    byte[] parametrized = new byte[code.length + params.length];
    System.arraycopy(code, 0, parametrized, 0, code.length);
    System.arraycopy(params, 0, parametrized, code.length, params.length);

    return parametrized;
  }

  @Override
  public byte[] getCode() {
    return code;
  }

  @Override
  public String toString() {
    return humanReadable();
  }

}
