package io.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.printer.TmT88vProfile;
import io.nmoncho.faradn.printer.escpos.EscPosRenderer;
import io.nmoncho.faradn.transport.DumpTransport;
import io.nmoncho.faradn.transport.PrinterNotReadyException;
import io.nmoncho.faradn.transport.PrinterStatus;
import io.nmoncho.faradn.transport.Transport;

public class PrinterTest {

  @Test
  void printRendersAndWritesToTheTransport() {
    Document doc = Document.from("<h1>Hi</h1>");
    DumpTransport transport = new DumpTransport();

    Printer.print(transport, doc, TmT88vProfile.INSTANCE);

    byte[] expected = new EscPosRenderer(TmT88vProfile.INSTANCE).render(doc.blocks());
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
        () -> Printer.print(notReady, Document.from("<p>x</p>"), TmT88vProfile.INSTANCE));
    assertFalse(written.get(), "must not write when the printer is not ready");
  }
}
