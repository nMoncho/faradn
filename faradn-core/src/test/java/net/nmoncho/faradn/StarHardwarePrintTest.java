//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import java.io.File;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import net.nmoncho.faradn.printer.StarProfiles;
import net.nmoncho.faradn.transport.NetworkTransport;
import net.nmoncho.faradn.transport.UsbTransport;

/**
 * Manual hardware checks for a Star Micronics TSP143IV, rendered natively in
 * StarPRNT (not ESC/POS emulation). Each talks to a real printer, so each runs
 * only when you point it at one:
 *
 * <pre>{@code
 * # over USB (Star Micronics vendor 0x0519)
 * mvn test -Dfaradn.star.hardware=true -Dtest=StarHardwarePrintTest
 *
 * # over Ethernet (raw TCP 9100)
 * mvn test -Dfaradn.star.host=192.168.1.50 -Dtest=StarHardwarePrintTest
 * }</pre>
 *
 * The printer must be left in native <strong>StarPRNT</strong> command mode
 * (the TSP143IV's out-of-the-box default), not Star Line, raster-only, or
 * ESC/POS emulation. Each test renders through the production path
 * ({@link Printer#print(net.nmoncho.faradn.transport.Transport, Document,
 * net.nmoncho.faradn.printer.PrinterProfile)} with
 * {@link StarProfiles#tsp143iv()}),
 * which also exercises the Phase&nbsp;5 status gate: a Star job must print
 * immediately, without the ~2-3&nbsp;s stall an ESC/POS {@code DLE EOT} probe
 * would cause.
 * <p>
 * {@link #printsReceiptOverUsb()} prints the full receipt (logo image, a table,
 * a Code&nbsp;128 barcode, a QR code and word-wrapped text). Verify by eye that
 * it is legible, then <strong>scan the barcode and the QR</strong> to confirm
 * the {@code ESC b} and {@code ESC GS y} framing is correct on paper.
 * {@link #printsTablesOverUsb()} prints the table showcase - verify the columns
 * line up, spanning cells cover the right width, and the box-drawing grid joins
 * cleanly. The cut at the end confirms {@code ESC d}.
 */
@Tag("hardware")
public class StarHardwarePrintTest {

  // Star Micronics USB vendor id. Confirm against the bundled vendor-ids.pdf
  // before relying on it in production.
  private static final int STAR_VENDOR_ID = 0x0519;

  private static final File RECEIPT = new File("src/test/resources/printjobs/receipt-full.html");
  private static final File TABLES = new File("src/test/resources/printjobs/tables.html");
  private static final File LINE_HEIGHT = new File("src/test/resources/printjobs/line-height.html");

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void printsReceiptOverUsb() {
    Document doc = Document.from(RECEIPT);
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      Printer.print(transport, doc, StarProfiles.tsp143iv());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void printsTablesOverUsb() {
    Document doc = Document.from(TABLES);
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      Printer.print(transport, doc, StarProfiles.tsp143iv());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.host", matches = ".+")
  void printsReceiptOverNetwork() {
    Document doc = Document.from(RECEIPT);
    String host = System.getProperty("faradn.star.host");
    try (NetworkTransport transport = new NetworkTransport(host)) {
      Printer.print(transport, doc, StarProfiles.tsp143iv());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void printsLineHeightOverUsb() {
    Document doc = Document.from(LINE_HEIGHT);
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      Printer.print(transport, doc, StarProfiles.tsp143iv());
    }
  }
}
