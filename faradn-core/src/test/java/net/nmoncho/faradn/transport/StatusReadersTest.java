//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.PrinterLanguage;

class StatusReadersTest {

  @Test
  void forLanguageSelectsTheReader() {
    assertInstanceOf(EscPosStatusReader.class, StatusReaders.forLanguage(PrinterLanguage.ESC_POS));
    assertInstanceOf(StarStatusReader.class, StatusReaders.forLanguage(PrinterLanguage.STAR_PRNT));
    assertThrows(IllegalArgumentException.class, () -> StatusReaders.forLanguage(null));
  }

  @Test
  void escPosReaderDelegatesToTransportStatus() {
    FakeTransport transport = new FakeTransport();
    transport.status = PrinterStatus.READY;

    PrinterStatus status = StatusReaders.forLanguage(PrinterLanguage.ESC_POS).read(transport);

    assertTrue(status.ready());
    assertTrue(transport.statusCalled);
  }

  @Test
  void starReaderPollsWithEscAckSohAndDecodesTheAsb() {
    FakeTransport transport = new FakeTransport();
    transport.reply = asb(block -> block[2] |= (1 << 5)); // cover open

    PrinterStatus status = StatusReaders.forLanguage(PrinterLanguage.STAR_PRNT).read(transport);

    assertArrayEquals(new byte[] { 0x1B, 0x06, 0x01 }, transport.lastRequest); // ESC ACK SOH
    assertTrue(status.coverOpen());
    assertFalse(status.ready());
  }

  @Test
  void starReaderReadsAHealthyBlockAsReady() {
    FakeTransport transport = new FakeTransport();
    transport.reply = asb(block -> {
    });

    assertTrue(StatusReaders.forLanguage(PrinterLanguage.STAR_PRNT).read(transport).ready());
  }

  @Test
  void starReaderIsOptimisticOnAnEmptyReply() {
    // An unusable reply must never *falsely* block a job.
    FakeTransport transport = new FakeTransport();
    transport.reply = new byte[0];

    assertTrue(StatusReaders.forLanguage(PrinterLanguage.STAR_PRNT).read(transport).ready());
  }

  @Test
  void starReaderPropagatesTransportFailure() {
    // A transport with no raw channel (the default) throws; the caller decides.
    Transport noExchange = new FakeTransport();

    assertThrows(TransportException.class,
        () -> StatusReaders.forLanguage(PrinterLanguage.STAR_PRNT).read(noExchange));
  }

  private interface BlockEdit {
    void apply(byte[] block);
  }

  private static byte[] asb(BlockEdit edit) {
    byte[] block = new byte[15];
    block[0] = 0x2F;
    block[1] = 0x0C;
    edit.apply(block);
    return block;
  }

  /** A transport that records the request and returns a canned exchange reply. */
  private static final class FakeTransport implements Transport {
    PrinterStatus status;
    boolean statusCalled;
    byte[] reply; // when null, exchange() falls back to the default (no raw channel)
    byte[] lastRequest;

    @Override
    public void write(byte[] payload) {
    }

    @Override
    public PrinterStatus status() {
      statusCalled = true;
      return status;
    }

    @Override
    public byte[] exchange(byte[] request, int maxReplyBytes) {
      if (reply == null) {
        return Transport.super.exchange(request, maxReplyBytes); // default: no raw channel
      }
      lastRequest = request;
      return reply;
    }

    @Override
    public void close() {
    }
  }
}
