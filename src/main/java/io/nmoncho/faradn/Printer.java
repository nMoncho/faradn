package io.nmoncho.faradn;

import io.nmoncho.faradn.printer.Devices;
import io.nmoncho.faradn.printer.PrinterProfile;
import io.nmoncho.faradn.printer.TmT88vProfile;
import io.nmoncho.faradn.printer.escpos.EscPosRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.usb.*;
import java.util.Map;
import java.util.Optional;

public class Printer {

  private static final Logger log = LoggerFactory.getLogger(Printer.class);

  private final UsbDevice device;

  private Printer(UsbDevice device) {
    this.device = device;
  }

  /**
   * Renders a {@link Document} for this printer's default profile and prints it.
   *
   * @param doc
   *        document to print
   */
  public void print(Document doc) {
    print(doc, TmT88vProfile.INSTANCE);
  }

  /**
   * Renders a {@link Document} for the given profile and prints it.
   *
   * @param doc
   *        document to print
   * @param profile
   *        capabilities of the target printer
   */
  public void print(Document doc, PrinterProfile profile) {
    final byte[] payload = new EscPosRenderer(profile).render(doc.blocks());

    Optional<Map.Entry<UsbInterface, UsbEndpoint>> ifaceEndpoint = Devices
        .findPrinterInterface(device)
        .flatMap(iface -> Devices.findOutEndpoint(iface).map(endpoint -> Map.entry(iface, endpoint)));

    if (ifaceEndpoint.isEmpty()) {
      log.warn("Couldn't find proper USB Interface and Endpoint for print. Nothing will be printed");
    }

    ifaceEndpoint.ifPresent(pair -> {
      log.debug(
          "Selected interface [{}] and endpoint [{}], from device [{}]",
          pair.getKey(),
          pair.getValue(),
          device);

      send(payload, pair.getKey(), pair.getValue());
    });
  }

  /**
   * Sends an already-rendered ESC/POS payload over the specified USB interface
   * and endpoint.
   *
   * @param payload
   *        ESC/POS bytes to send
   * @param iface
   *        interface to use for printing
   * @param endpoint
   *        endpoint to use for printing
   */
  private void send(byte[] payload, UsbInterface iface, UsbEndpoint endpoint) {
    UsbPipe pipe = null;

    try {
      iface.claim();
      pipe = endpoint.getUsbPipe();

      pipe.open();

      pipe.syncSubmit(payload);
    } catch (UsbNotActiveException | UsbDisconnectedException | UsbException ex) {
      throw new PrintingException("Something when wrong while trying to print", ex);
    } finally {
      try {
        if (pipe != null) {
          pipe.close();
        }
      } catch (UsbException ignored) {
      }
      try {
        if (iface != null) {
          iface.release();
        }
      } catch (UsbException ignored) {
      }
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
