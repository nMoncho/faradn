package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.escpos.EscPosRenderer;
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
}
