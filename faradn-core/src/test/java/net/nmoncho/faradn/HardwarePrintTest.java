package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.List;
import java.util.Optional;

import javax.usb.UsbDevice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Cut;
import net.nmoncho.faradn.document.Feed;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.Devices;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.escpos.EscPosRenderer;
import net.nmoncho.faradn.transport.NetworkTransport;
import net.nmoncho.faradn.transport.UsbTransport;

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
 * The receipt tests print a full receipt exercising the logo image, a table, a
 * Code 128 barcode, a QR code and word-wrapped text (checkpoint 3), plus
 * heading
 * sizes, bold, centering, alignment, underline and rules (checkpoint 1). Verify
 * by eye that it is legible, scan the barcode and QR, and - checkpoint 2 - pull
 * the paper roll to confirm the job fails rather than hangs.
 * <p>
 * {@link #printsTableShowcaseOverUsb()} prints a separate document of several
 * table types - content-sized columns, full-width and partial {@code colspan},
 * inline-styled cells, and a four-column table - to check the character-grid
 * layout on paper: verify each table's columns line up, spanning cells cover
 * the
 * right width, and narrow columns are not padded to an even split.
 * <p>
 * {@link #printsPageModeCanvasOverUsb()} prints a fixed-size {@code Canvas}
 * (page
 * mode): text placed at exact dot positions. Verify the pieces land where their
 * {@code (x, y)} say - e.g. two labels on the same row at different x, and a
 * line
 * lower down - and that the region prints as one block.
 * <p>
 * {@link #printsPageModeHtmlOverUsb()} prints the same coupon through the full
 * HTML pipeline (a {@code position: relative} container with {@code position:
 * absolute} children), verifying the CSS-to-page-mode mapping end to end.
 */
@Tag("hardware")
public class HardwarePrintTest {

  private static final File RECEIPT = new File("src/test/resources/printjobs/receipt-full.html");
  private static final File TABLES = new File("src/test/resources/printjobs/tables.html");
  private static final File PAGE_MODE = new File("src/test/resources/printjobs/page-mode.html");

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsTextReceiptOverUsb() {
    Document doc = Document.from(RECEIPT);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsTableShowcaseOverUsb() {
    Document doc = Document.from(TABLES);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsPageModeCanvasOverUsb() {
    PrinterProfile profile = PrinterProfile.load("TM-T88V").orElseThrow();
    ComputedStyle plain = ComputedStyle.INITIAL;
    ComputedStyle bold = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);

    // A fixed-size page-mode region: children placed at exact dot positions.
    Canvas canvas = Canvas.of(profile.dotsPerLine(), 160)
        .place(0, 0, new Paragraph(List.of(new TextRun("PAGE MODE COUPON", bold)), Alignment.LEFT))
        .place(0, 56, new Paragraph(List.of(new TextRun("left @ x=0", plain)), Alignment.LEFT))
        .place(280, 56, new Paragraph(List.of(new TextRun("mid @ x=280", plain)), Alignment.LEFT))
        .place(0, 104, new Paragraph(List.of(new TextRun("bottom @ y=104", plain)), Alignment.LEFT))
        .build();
    byte[] job = new EscPosRenderer(profile).render(List.of(canvas, new Feed(3), new Cut(true)));

    Optional<UsbDevice> device = Devices.findDevice((short) 0x04b8);
    device.ifPresentOrElse(dev -> {
      try (UsbTransport transport = new UsbTransport(dev)) {
        transport.write(job);
      }
    }, () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.hardware", matches = "true")
  void printsPageModeHtmlOverUsb() {
    // The full HTML -> IR -> page-mode path: same coupon as the programmatic
    // test, driven from position:relative / position:absolute CSS.
    Document doc = Document.from(PAGE_MODE);

    Optional<Printer> printer = Printer.from(0x04b8);
    printer.ifPresentOrElse(
        p -> p.print(doc, "TM-T88V"),
        () -> fail("No Epson printer (USB vendor 0x04b8) found"));
  }

  @Test
  @EnabledIfSystemProperty(named = "faradn.printer.host", matches = ".+")
  void printsTextReceiptOverNetwork() {
    Document doc = Document.from(RECEIPT);
    String host = System.getProperty("faradn.printer.host");

    try (NetworkTransport transport = new NetworkTransport(host)) {
      Printer.print(transport, doc, PrinterProfile.load("TM-T88V").orElseThrow());
    }
  }
}
