package net.nmoncho.faradn.cli;

import net.nmoncho.faradn.transport.NetworkTransport;
import net.nmoncho.faradn.transport.Transport;
import net.nmoncho.faradn.transport.UsbTransport;

/**
 * Opens a {@link Transport} to the printer named by the CLI options: a network
 * host ({@code host[:port]}) or a USB printer ({@code vid[:pid]} in hex).
 */
final class Targets {

  private Targets() {
  }

  static Transport open(String printer, String host) {
    if (host != null) {
      return network(host);
    }
    if (printer != null) {
      return usb(printer);
    }
    throw new IllegalArgumentException("Specify a target with --printer, --host, or --dry-run");
  }

  private static Transport network(String host) {
    final int colon = host.lastIndexOf(':');
    if (colon < 0) {
      return new NetworkTransport(host);
    }
    return new NetworkTransport(host.substring(0, colon), Integer.parseInt(host.substring(colon + 1)));
  }

  private static Transport usb(String printer) {
    final String[] parts = printer.split(":", 2);
    final int vendorId = parseHex(parts[0]);
    return parts.length > 1
        ? UsbTransport.open(vendorId, parseHex(parts[1]))
        : UsbTransport.open(vendorId);
  }

  private static int parseHex(String value) {
    String text = value.trim().toLowerCase();
    if (text.startsWith("0x")) {
      text = text.substring(2);
    }
    return Integer.parseInt(text, 16);
  }
}
