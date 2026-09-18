//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import java.io.File;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.StarProfiles;
import net.nmoncho.faradn.transport.NetworkTransport;
import net.nmoncho.faradn.transport.PrinterStatus;
import net.nmoncho.faradn.transport.StatusReaders;
import net.nmoncho.faradn.transport.UsbTransport;
import net.nmoncho.faradn.printer.PrinterLanguage;

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
 * net.nmoncho.faradn.printer.PrinterProfile)} with the database
 * {@code "star-tsp143iv"} profile, or {@link StarProfiles#tsp143ivJapanese()}
 * for the Kanji test), which also exercises the Phase&nbsp;5 status gate: a
 * Star
 * job must print
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
  private static final File KANJI = new File("src/test/resources/printjobs/kanji.html");

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void printsReceiptOverUsb() {
    Document doc = Document.from(RECEIPT);
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      Printer.print(transport, doc, PrinterProfile.load("star-tsp143iv").orElseThrow());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void printsTablesOverUsb() {
    Document doc = Document.from(TABLES);
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      Printer.print(transport, doc, PrinterProfile.load("star-tsp143iv").orElseThrow());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void printsKanjiReceiptOverUsb() {
    // Mixed Japanese/ASCII receipt through StarPRNT's UTF-8 Kanji path
    // (ESC GS ) U). Requires a TSP143IV with a Japanese Kanji font
    // (the Japanese profile); an overseas unit prints the ideographs blank.
    Document doc = Document.from(KANJI);
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      Printer.print(transport, doc, StarProfiles.tsp143ivJapanese());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.host", matches = ".+")
  void printsReceiptOverNetwork() {
    Document doc = Document.from(RECEIPT);
    String host = System.getProperty("faradn.star.host");
    try (NetworkTransport transport = new NetworkTransport(host)) {
      Printer.print(transport, doc, PrinterProfile.load("star-tsp143iv").orElseThrow());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void printsLineHeightOverUsb() {
    Document doc = Document.from(LINE_HEIGHT);
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      Printer.print(transport, doc, PrinterProfile.load("star-tsp143iv").orElseThrow());
    }
  }

  /**
   * Reads the Star ASB status over USB and prints both the raw reply and the
   * decoded readiness, so the ASB bit map and any transport framing can be
   * confirmed on real hardware. Run it in a few states - normal, cover open,
   * paper out - and check the decode matches:
   *
   * <pre>{@code
   * mvn test -Dfaradn.star.hardware=true -Dtest=StarHardwarePrintTest#readsStarStatusOverUsb
   * }</pre>
   *
   * This does not print anything and never refuses; it only reads. Once the
   * decode is confirmed, {@code PrinterLanguage.supportsRealtimeStatus()} can be
   * flipped on for StarPRNT to make the pre-flight check live.
   */
  @Test
  @EnabledIfSystemProperty(named = "faradn.star.hardware", matches = "true")
  void readsStarStatusOverUsb() {
    try (UsbTransport transport = UsbTransport.open(STAR_VENDOR_ID)) {
      byte[] raw = transport.exchange(new byte[] { 0x1B, 0x06, 0x01 }, 64); // ESC ACK SOH
      System.out.println("Star ASB raw reply (" + raw.length + " bytes): " + hex(raw));

      PrinterStatus status = StatusReaders.forLanguage(PrinterLanguage.STAR_PRNT).read(transport);
      System.out.println("Decoded status: " + status + " (ready=" + status.ready() + ")");
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString().trim();
  }
}
