package io.nmoncho.faradn.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DumpTransportTest {

  @Test
  void capturesEverythingWritten() {
    DumpTransport transport = new DumpTransport();

    transport.write(new byte[] { 1, 2, 3 });
    transport.write(new byte[] { 4, 5 });

    assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, transport.bytes());
  }

  @Test
  void reportsReady() {
    assertEquals(PrinterStatus.READY, new DumpTransport().status());
    assertTrue(new DumpTransport().status().ready());
  }
}
