//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

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

  // ----- StarPRNT ASB decode (block bytes 3/4/6/7 = indices 2/3/5/6) -----

  /** A 15-byte ASB v6 block with the two header bytes and everything OK. */
  private static byte[] asb() {
    byte[] block = new byte[15];
    block[0] = 0x2F; // Header-1
    block[1] = 0x0C; // Header-2
    return block;
  }

  @Test
  void starAsbReadyWhenNothingIsWrong() {
    PrinterStatus status = PrinterStatus.ofStarAsb(asb());

    assertTrue(status.online());
    assertFalse(status.coverOpen());
    assertFalse(status.paperEnd());
    assertFalse(status.error());
    assertTrue(status.ready());
  }

  @Test
  void starAsbCoverOpenFromThirdByteBit5() {
    byte[] block = asb();
    block[2] |= (1 << 5);

    PrinterStatus status = PrinterStatus.ofStarAsb(block);

    assertTrue(status.coverOpen());
    assertFalse(status.ready());
  }

  @Test
  void starAsbOfflineFromThirdByteBit3() {
    byte[] block = asb();
    block[2] |= (1 << 3);

    PrinterStatus status = PrinterStatus.ofStarAsb(block);

    assertFalse(status.online());
    assertFalse(status.ready());
  }

  @Test
  void starAsbCutterErrorFromFourthByteBit3() {
    byte[] block = asb();
    block[3] |= (1 << 3);

    PrinterStatus status = PrinterStatus.ofStarAsb(block);

    assertTrue(status.error());
    assertFalse(status.ready());
  }

  @Test
  void starAsbPaperEndFromSixthByteBit3() {
    byte[] block = asb();
    block[5] |= (1 << 3);

    PrinterStatus status = PrinterStatus.ofStarAsb(block);

    assertTrue(status.paperEnd());
    assertFalse(status.ready());
  }

  @Test
  void starAsbNearEndFromSeventhByteBit1DoesNotBlock() {
    byte[] block = asb();
    block[6] |= (1 << 1);

    PrinterStatus status = PrinterStatus.ofStarAsb(block);

    assertTrue(status.paperNearEnd());
    assertFalse(status.paperEnd());
    assertTrue(status.ready());
  }

  @Test
  void starAsbTooShortIsRejected() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> PrinterStatus.ofStarAsb(new byte[] { 0x2F, 0x0C }));
  }
}
