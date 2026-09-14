//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import net.nmoncho.faradn.document.Border;
import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.ZebraProfiles;
import net.nmoncho.faradn.printer.zpl.ZplRenderer;
import net.nmoncho.faradn.transport.NetworkTransport;
import net.nmoncho.faradn.transport.UsbTransport;

/**
 * Manual hardware checks for a Zebra ZD421, rendered natively as ZPL II and
 * EPL2. Each talks to a real printer, so each runs only when you point it at
 * one:
 *
 * <pre>{@code
 * # over USB (Zebra Technologies vendor 0x0A5F)
 * mvn test -Dfaradn.zebra.hardware=true -Dtest=ZebraHardwarePrintTest
 *
 * # over Ethernet (raw TCP 9100)
 * mvn test -Dfaradn.zebra.host=192.168.1.50 -Dtest=ZebraHardwarePrintTest
 * }</pre>
 *
 * Leave the printer in its default {@code device.languages = epl_zpl}
 * auto-sensing mode (or force the matching language) so the same unit prints
 * both the ZPL and the EPL jobs, and make sure it is calibrated to the loaded
 * media. Each label test renders through the production path
 * ({@link Printer#print(net.nmoncho.faradn.transport.Transport, Document,
 * PrinterProfile)}); the label languages report no realtime status, so a job
 * must print immediately, without the {@code DLE EOT} stall an ESC/POS profile
 * would cause.
 * <p>
 * {@link #printsZplLabelOverUsb()} / {@link #printsEplLabelOverUsb()} print the
 * shipping-label fixture (logo image, a Code&nbsp;128 barcode, a QR code and
 * text) in each language. Verify legibility by eye, then <strong>scan the
 * barcode and the QR</strong> to confirm the native barcode fields are correct
 * on paper - note QR is expected to fail on a general (non-Japanese) ZD421 in
 * EPL, which is why a ZPL profile is preferred for QR. For the EPL logo, also
 * confirm it prints black-on-white and not inverted: EPL {@code GW} assumes
 * {@code 0} = black (the inverse of ZPL), an assumption that stays silent until
 * printed. {@link
 * #printsBorderedBoxOverUsb()} prints a programmatic bordered label so the
 * native {@code ^GB} box is visible; also confirm on paper whether the 90/270
 * rotation anchor lands where intended (the one geometry item the unit tests
 * cannot settle).
 */
@Tag("hardware")
public class ZebraHardwarePrintTest {

  // Zebra Technologies USB vendor id (2655 decimal), from the bundled vendor-ids.pdf.
  // Match on this VID plus the USB printer class; the product id varies per model.
  private static final int ZEBRA_VENDOR_ID = 0x0A5F;

  private static final File ZEBRA_LABEL = new File("src/test/resources/printjobs/zebra-label.html");
  private static final File LABEL = new File("src/test/resources/printjobs/label.html");

  @Test
  @EnabledIfSystemProperty(named = "faradn.zebra.hardware", matches = "true")
  void printsZplLabelOverUsb() {
    Document doc = Document.from(ZEBRA_LABEL);
    try (UsbTransport transport = UsbTransport.open(ZEBRA_VENDOR_ID)) {
      Printer.print(transport, doc, ZebraProfiles.zd421Zpl203());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.zebra.hardware", matches = "true")
  void printsEplLabelOverUsb() {
    Document doc = Document.from(ZEBRA_LABEL);
    try (UsbTransport transport = UsbTransport.open(ZEBRA_VENDOR_ID)) {
      Printer.print(transport, doc, ZebraProfiles.zd421Epl203());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.zebra.hardware", matches = "true")
  void printsGenericLabelZplOverUsb() {
    // The pre-existing generic label fixture (label.html), printed via the Zebra
    // ZPL profile - so both label fixtures are exercised on Zebra hardware.
    Document doc = Document.from(LABEL);
    try (UsbTransport transport = UsbTransport.open(ZEBRA_VENDOR_ID)) {
      Printer.print(transport, doc, ZebraProfiles.zd421Zpl203());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.zebra.hardware", matches = "true")
  void printsGenericLabelEplOverUsb() {
    Document doc = Document.from(LABEL);
    try (UsbTransport transport = UsbTransport.open(ZEBRA_VENDOR_ID)) {
      Printer.print(transport, doc, ZebraProfiles.zd421Epl203());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.zebra.host", matches = ".+")
  void printsZplLabelOverNetwork() {
    Document doc = Document.from(ZEBRA_LABEL);
    String host = System.getProperty("faradn.zebra.host");
    try (NetworkTransport transport = new NetworkTransport(host)) {
      Printer.print(transport, doc, ZebraProfiles.zd421Zpl203());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.zebra.host", matches = ".+")
  void printsEplLabelOverNetwork() {
    Document doc = Document.from(ZEBRA_LABEL);
    String host = System.getProperty("faradn.zebra.host");
    try (NetworkTransport transport = new NetworkTransport(host)) {
      Printer.print(transport, doc, ZebraProfiles.zd421Epl203());
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.zebra.hardware", matches = "true")
  void printsBorderedBoxOverUsb() {
    // A programmatic Canvas with a bordered paragraph, so the native ^GB box is
    // visible on paper (the HTML fixture omits the border; it is unit-tested).
    PrinterProfile profile = ZebraProfiles.zd421Zpl203();
    Canvas canvas = Canvas.of(400, 200)
        .place(20, 20, new Paragraph(List.of(new TextRun("BOXED LABEL", ComputedStyle.INITIAL)), Alignment.LEFT,
            Border.all(Border.Style.SINGLE)))
        .build();
    try (UsbTransport transport = UsbTransport.open(ZEBRA_VENDOR_ID)) {
      transport.write(new ZplRenderer(profile).render(List.of(canvas)));
    }
  }
}
