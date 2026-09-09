package net.nmoncho.faradn.cli;

import java.util.List;
import java.util.concurrent.Callable;

import net.nmoncho.faradn.printer.Devices;
import net.nmoncho.faradn.printer.UsbPrinter;

import picocli.CommandLine.Command;

/**
 * Lists the USB printers connected to this machine. Network printers are
 * addressed directly with {@code print --host}.
 */
@Command(name = "list", description = "List connected USB printers.")
final class ListCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    final List<UsbPrinter> printers = Devices.list();
    if (printers.isEmpty()) {
      System.out.println("No USB printers found.");
      return 0;
    }

    System.out.println("USB printers:");
    for (UsbPrinter printer : printers) {
      final String vendor = Devices.vendorName(printer.vendorId()).orElse("N/A");
      System.out.printf("  0x%04x:0x%04x (%s)%n", printer.vendorId(), printer.productId(), vendor);
    }
    return 0;
  }
}
