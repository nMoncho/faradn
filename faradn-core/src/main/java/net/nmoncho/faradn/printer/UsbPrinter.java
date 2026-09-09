package net.nmoncho.faradn.printer;

/**
 * A connected USB printer, identified by its USB vendor and product ids.
 * <p>
 * This is the neutral value type the public API exposes for USB discovery, so
 * callers do not depend on {@code javax.usb}. Open a transport to one with
 * {@link net.nmoncho.faradn.transport.UsbTransport#open(int, int)} or
 * {@link net.nmoncho.faradn.Printer#from(int, int)}.
 *
 * @param vendorId
 *        the USB vendor id (e.g. {@code 0x04b8} for Epson)
 * @param productId
 *        the USB product id
 */
public record UsbPrinter(int vendorId, int productId) {
}
