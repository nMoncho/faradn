package io.nmoncho.faradn.transport;

import java.io.ByteArrayOutputStream;

/**
 * A transport that captures bytes instead of sending them anywhere: handy for
 * tests, dry runs, and dumping a rendered job to inspect it. It always reports
 * {@link PrinterStatus#READY}.
 */
public final class DumpTransport implements Transport {

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  @Override
  public void write(byte[] payload) {
    buffer.writeBytes(payload);
  }

  @Override
  public PrinterStatus status() {
    return PrinterStatus.READY;
  }

  @Override
  public void close() {
    // nothing to release
  }

  /** Returns a copy of the bytes written so far. */
  public byte[] bytes() {
    return buffer.toByteArray();
  }
}
