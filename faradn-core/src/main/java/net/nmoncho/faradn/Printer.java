//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.nmoncho.faradn.internal.usb.UsbDevices;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.EscPosRenderer;
import net.nmoncho.faradn.transport.PrinterNotReadyException;
import net.nmoncho.faradn.transport.PrinterStatus;
import net.nmoncho.faradn.transport.Transport;
import net.nmoncho.faradn.transport.TransportException;
import net.nmoncho.faradn.transport.UsbTransport;

/**
 * Prints a {@link Document} to a USB printer, or to any {@link Transport}.
 * <p>
 * <strong>Thread-safety:</strong> a {@code Printer} is immutable and safe to
 * share, but each {@link #print(Document, String)} / {@link #status()} call
 * opens
 * its own {@link UsbTransport}. A printer serializes work at the device, so do
 * not run concurrent jobs against the same one.
 */
public class Printer {

  private static final Logger log = LoggerFactory.getLogger(Printer.class);

  private final int vendorId;
  private final int productId; // negative means "any product for this vendor"

  private Printer(int vendorId, int productId) {
    this.vendorId = vendorId;
    this.productId = productId;
  }

  /**
   * Renders a {@link Document} for the named profile and prints it over USB.
   * The profile is loaded from the capability database
   * ({@link PrinterProfile#load(String)}); the job fails if no profile matches
   * the name.
   *
   * @param doc
   *        document to print
   * @param profileName
   *        device name to look up, e.g. {@code "TM-T88V"}
   * @throws IllegalArgumentException
   *         if no profile matches {@code profileName}
   * @throws PrinterNotReadyException
   *         if the printer reports it is not ready (offline, cover open, out of
   *         paper)
   * @throws net.nmoncho.faradn.transport.TransportException
   *         if the USB printer cannot be opened or written to
   */
  public void print(Document doc, String profileName) {
    print(doc, profile(profileName));
  }

  /**
   * Renders a {@link Document} for the given profile and prints it over USB.
   *
   * @param doc
   *        document to print
   * @param profile
   *        capabilities of the target printer
   * @throws PrinterNotReadyException
   *         if the printer reports it is not ready
   * @throws net.nmoncho.faradn.transport.TransportException
   *         if the USB printer cannot be opened or written to
   */
  public void print(Document doc, PrinterProfile profile) {
    try (UsbTransport transport = openTransport()) {
      print(transport, doc, profile);
    }
  }

  /**
   * Reads the printer's real-time status over USB.
   *
   * @return the decoded status
   * @throws net.nmoncho.faradn.transport.TransportException
   *         if the USB printer cannot be opened or its status cannot be read
   */
  public PrinterStatus status() {
    try (UsbTransport transport = openTransport()) {
      return transport.status();
    }
  }

  private UsbTransport openTransport() {
    return productId < 0 ? UsbTransport.open(vendorId) : UsbTransport.open(vendorId, productId);
  }

  /**
   * Renders a {@link Document} and writes it to any {@link Transport}, after a
   * best-effort pre-flight readiness check. This is the transport-agnostic core
   * shared by the USB convenience methods and callers that supply their own
   * transport (for example a
   * {@link net.nmoncho.faradn.transport.NetworkTransport}).
   *
   * @param transport
   *        where to send the rendered job
   * @param doc
   *        document to print
   * @param profile
   *        capabilities of the target printer
   * @throws PrinterNotReadyException
   *         if the pre-flight check reports the printer is not ready
   * @throws net.nmoncho.faradn.transport.TransportException
   *         if the payload cannot be written to the transport
   */
  public static void print(Transport transport, Document doc, PrinterProfile profile) {
    final byte[] payload = new EscPosRenderer(profile).render(doc.blocks(profile.dpi()));
    ensureReady(transport);
    transport.write(payload);
  }

  private static PrinterProfile profile(String name) {
    return PrinterProfile.load(name)
        .orElseThrow(() -> new IllegalArgumentException("No printer profile found for '" + name + "'"));
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
    return UsbDevices
        .findDevice((short) vendorId)
        .map(device -> new Printer(vendorId, -1));
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
    return UsbDevices
        .findDevice((short) vendorId, (short) productId)
        .map(device -> new Printer(vendorId, productId));
  }

}
