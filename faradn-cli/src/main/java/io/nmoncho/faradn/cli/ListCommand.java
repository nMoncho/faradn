package io.nmoncho.faradn.cli;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;

import io.nmoncho.faradn.printer.Devices;

import picocli.CommandLine.Command;

/**
 * Lists the USB printers connected to this machine. Network printers are
 * addressed directly with {@code print --host}.
 */
@Command(name = "list", description = "List connected USB printers.")
final class ListCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    final List<UsbDevice> printers = Devices.listPrinterDevices();
    if (printers.isEmpty()) {
      System.out.println("No USB printers found.");
      return 0;
    }

    System.out.println("USB printers:");
    for (UsbDevice printer : printers) {
      final UsbDeviceDescriptor descriptor = printer.getUsbDeviceDescriptor();
      final String vendor = Devices.findVendorName(descriptor.idVendor()).orElse("N/A");
      System.out.printf(
          "  0x%04x:0x%04x (%s)%n",
          descriptor.idVendor() & 0xFFFF,
          descriptor.idProduct() & 0xFFFF,
          vendor);
    }
    return 0;
  }
}
