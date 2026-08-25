package io.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nmoncho.faradn.printer.TmT88vProfile;
import io.nmoncho.faradn.transport.NetworkTransport;

/**
 * Manual hardware checks (Hardware checkpoints 1 and 2). Each talks to a real
 * printer, so each runs only when you point it at one:
 *
 * <pre>{@code
 * # over USB (Epson vendor 0x04b8)
 * mvn test -Dfaradn.hardware=true -Dtest=HardwarePrintTest
 *
 * # over Ethernet (raw TCP 9100)
 * mvn test -Dfaradn.printer.host=192.168.1.50 -Dtest=HardwarePrintTest
 * }</pre>
 *
 * Both print a text-only receipt; verify by eye that heading sizes, bold,
 * centering, alignment, underline and rules come out right, and — checkpoint 2
 * —
 * that pulling the paper roll makes the job fail rather than hang. Images and
 * barcodes are a later milestone, so the fixture is deliberately text only.
 */
@Tag("hardware")
public class HardwarePrintTest {

  private static final File RECEIPT = new File("src/test/resources/printjobs/receipt-text.html");

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsTextReceiptOverUsb() {
    Document doc = Document.from(RECEIPT);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.printer.host", matches = ".+")
  void printsTextReceiptOverNetwork() {
    Document doc = Document.from(RECEIPT);
    String host = System.getProperty("faradn.printer.host");

    try (NetworkTransport transport = new NetworkTransport(host)) {
      Printer.print(transport, doc, TmT88vProfile.INSTANCE);
    }
  }
}
