//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.util.List;
import java.util.Optional;

import net.nmoncho.faradn.internal.usb.UsbDevices;

/**
 * Discovery of connected USB printers, as neutral {@link UsbPrinter} values.
 * <p>
 * This is the public entry point for enumerating USB printers without depending
 * on {@code javax.usb}; the low-level USB handling lives in an internal
 * package.
 * To print, open a transport with
 * {@link net.nmoncho.faradn.transport.UsbTransport#open(int, int)} or use
 * {@link net.nmoncho.faradn.Printer#from(int)}.
 */
public final class Devices {

  private Devices() {
  }

  /**
   * Lists the USB printers connected to this machine.
   *
   * @return the connected printers, empty when none are found
   */
  public static List<UsbPrinter> list() {
    return UsbDevices.listPrinters();
  }

  /**
   * The registered vendor name for a USB vendor id, from the bundled vendor
   * database.
   *
   * @param vendorId
   *        the USB vendor id
   * @return the vendor name, if known
   */
  public static Optional<String> vendorName(int vendorId) {
    return UsbDevices.findVendorName((short) vendorId);
  }
}
