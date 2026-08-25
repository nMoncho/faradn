package io.nmoncho.faradn.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PrinterStatusTest {

  /** A DLE EOT reply's fixed-bit baseline (bits 1 and 4 set), nothing wrong. */
  private static final byte OK = 0x12;

  @Test
  void readyWhenNothingIsWrong() {
    PrinterStatus status = PrinterStatus.of(OK, OK, OK, OK);

    assertTrue(status.online());
    assertFalse(status.coverOpen());
    assertFalse(status.paperEnd());
    assertFalse(status.error());
    assertTrue(status.ready());
  }

  @Test
  void paperOutIsDetectedFromThePaperSensor() {
    byte paperOut = (byte) (OK | (1 << 5) | (1 << 6));

    PrinterStatus status = PrinterStatus.of(OK, OK, OK, paperOut);

    assertTrue(status.paperEnd());
    assertFalse(status.ready());
  }

  @Test
  void coverOpenIsDetected() {
    byte coverOpen = (byte) (OK | (1 << 2));

    PrinterStatus status = PrinterStatus.of(OK, coverOpen, OK, OK);

    assertTrue(status.coverOpen());
    assertFalse(status.ready());
  }

  @Test
  void offlineIsDetected() {
    byte offline = (byte) (OK | (1 << 3));

    PrinterStatus status = PrinterStatus.of(offline, OK, OK, OK);

    assertFalse(status.online());
    assertFalse(status.ready());
  }

  @Test
  void errorIsDetected() {
    byte error = (byte) (OK | (1 << 6));

    PrinterStatus status = PrinterStatus.of(OK, OK, error, OK);

    assertTrue(status.error());
    assertFalse(status.ready());
  }

  @Test
  void paperNearEndDoesNotBlockPrinting() {
    byte nearEnd = (byte) (OK | (1 << 2) | (1 << 3));

    PrinterStatus status = PrinterStatus.of(OK, OK, OK, nearEnd);

    assertTrue(status.paperNearEnd());
    assertFalse(status.paperEnd());
    assertTrue(status.ready());
  }
}
