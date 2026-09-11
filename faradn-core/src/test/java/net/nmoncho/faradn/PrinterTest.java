//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.EscPosRenderer;
import net.nmoncho.faradn.printer.StarProfiles;
import net.nmoncho.faradn.transport.DumpTransport;
import net.nmoncho.faradn.transport.PrinterNotReadyException;
import net.nmoncho.faradn.transport.PrinterStatus;
import net.nmoncho.faradn.transport.Transport;

public class PrinterTest {

  private static final PrinterProfile TM_T88V = PrinterProfile.load("TM-T88V").orElseThrow();

  @Test
  void printRendersAndWritesToTheTransport() {
    Document doc = Document.from("<h1>Hi</h1>");
    DumpTransport transport = new DumpTransport();

    Printer.print(transport, doc, TM_T88V);

    byte[] expected = new EscPosRenderer(TM_T88V).render(doc.blocks());
    assertArrayEquals(expected, transport.bytes());
  }

  @Test
  void printRefusesWhenPrinterNotReady() {
    AtomicBoolean written = new AtomicBoolean(false);
    Transport notReady = new Transport() {
      @Override
      public void write(byte[] payload) {
        written.set(true);
      }

      @Override
      public PrinterStatus status() {
        return new PrinterStatus(true, true, false, false, false); // cover open
      }

      @Override
      public void close() {
        // nothing to release
      }
    };

    assertThrows(PrinterNotReadyException.class,
        () -> Printer.print(notReady, Document.from("<p>x</p>"), TM_T88V));
    assertFalse(written.get(), "must not write when the printer is not ready");
  }

  /** A transport that counts status() polls and records the bytes written. */
  private static final class CountingTransport implements Transport {
    final AtomicInteger statusPolls = new AtomicInteger();
    volatile byte[] written;

    @Override
    public void write(byte[] payload) {
      written = payload;
    }

    @Override
    public PrinterStatus status() {
      statusPolls.incrementAndGet();
      return PrinterStatus.READY;
    }

    @Override
    public void close() {
      // nothing to release
    }
  }

  @Test
  void skipsTheStatusProbeForAStarProfile() {
    // StarPRNT has no DLE EOT reply, so the pre-flight poll must be skipped.
    CountingTransport transport = new CountingTransport();

    Printer.print(transport, Document.from("<p>x</p>"), StarProfiles.tsp143iv());

    assertEquals(0, transport.statusPolls.get(), "Star jobs must not poll status");
    assertTrue(transport.written != null && transport.written.length > 0, "the job must still be written");
  }

  @Test
  void runsTheStatusProbeForAnEscPosProfile() {
    CountingTransport transport = new CountingTransport();

    Printer.print(transport, Document.from("<p>x</p>"), TM_T88V);

    assertEquals(1, transport.statusPolls.get(), "ESC/POS jobs keep the pre-flight poll");
  }
}
