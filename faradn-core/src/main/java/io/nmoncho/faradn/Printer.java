package io.nmoncho.faradn;

import java.util.Optional;

import javax.usb.UsbDevice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nmoncho.faradn.printer.Devices;
import io.nmoncho.faradn.printer.PrinterProfile;
import io.nmoncho.faradn.printer.TmT88vProfile;
import io.nmoncho.faradn.printer.escpos.EscPosRenderer;
import io.nmoncho.faradn.transport.PrinterNotReadyException;
import io.nmoncho.faradn.transport.PrinterStatus;
import io.nmoncho.faradn.transport.Transport;
import io.nmoncho.faradn.transport.TransportException;
import io.nmoncho.faradn.transport.UsbTransport;

public class Printer {

  private static final Logger log = LoggerFactory.getLogger(Printer.class);

  private final UsbDevice device;

  private Printer(UsbDevice device) {
    this.device = device;
  }

  /**
   * Renders a {@link Document} for this printer's default profile and prints it
   * over USB.
   *
   * @param doc
   *        document to print
   */
  public void print(Document doc) {
    print(doc, TmT88vProfile.INSTANCE);
  }

  /**
   * Renders a {@link Document} for the given profile and prints it over USB.
   *
   * @param doc
   *        document to print
   * @param profile
   *        capabilities of the target printer
   */
  public void print(Document doc, PrinterProfile profile) {
    try (UsbTransport transport = new UsbTransport(device)) {
      print(transport, doc, profile);
    }
  }

  /**
   * Reads the printer's real-time status over USB.
   *
   * @return the decoded status
   */
  public PrinterStatus status() {
    try (UsbTransport transport = new UsbTransport(device)) {
      return transport.status();
    }
  }

  /**
   * Renders a {@link Document} and writes it to any {@link Transport}, after a
   * best-effort pre-flight readiness check. This is the transport-agnostic core
   * shared by the USB convenience methods and callers that supply their own
   * transport (for example a
   * {@link io.nmoncho.faradn.transport.NetworkTransport}).
   *
   * @param transport
   *        where to send the rendered job
   * @param doc
   *        document to print
   * @param profile
   *        capabilities of the target printer
   */
  public static void print(Transport transport, Document doc, PrinterProfile profile) {
    final byte[] payload = new EscPosRenderer(profile).render(doc.blocks());
    ensureReady(transport);
    transport.write(payload);
  }

  /**
   * Best-effort pre-flight check: if the status channel works and reports a
   * problem, refuse the job; if status cannot be read, print anyway.
   */
  private static void ensureReady(Transport transport) {
    final PrinterStatus status;
    try {
      status = transport.status();
    } catch (TransportException e) {
      log.debug("Skipping pre-flight status check: {}", e.getMessage());
      return;
    }
    if (!status.ready()) {
      throw new PrinterNotReadyException(status);
    }
  }

  /**
   * Finds a printer by `vendorId`, as defined in <a href=
   * "https://www.usb.org/sites/default/files/vendor_ids032322.pdf_1.pdf">Valid
   * USB Vendor ID Numbers</a>.
   * This method will pick up the first device having this vendor id, if there are
   * more than one.
   *
   * @param vendorId
   *        Vendor ID for the Printer you want use.
   * @return Some printer if found, empty otherwise
   */
  public static Optional<Printer> from(int vendorId) {
    return Devices
        .findDevice((short) vendorId)
        .map(Printer::new);
  }

  /**
   * Finds a printer by `vendorId` and `productId`, as defined in <a href=
   * "https://www.usb.org/sites/default/files/vendor_ids032322.pdf_1.pdf">Valid
   * USB Vendor ID Numbers</a>.
   * This method will pick up the first device having this vendor id, if there are
   * more than one.
   *
   * @param vendorId
   *        Vendor ID for the Printer you want use.
   * @param productId
   *        Printer ID you want to use.
   * @return Some printer if found, empty otherwise
   */
  public static Optional<Printer> from(int vendorId, int productId) {
    return Devices
        .findDevice((short) vendorId, (short) productId)
        .map(Printer::new);
  }

}
