package io.nmoncho.faradn.cli;

import io.nmoncho.faradn.printer.PrinterProfile;

/**
 * Resolves a printer profile by device name from the capability database.
 */
final class Profiles {

  private Profiles() {
  }

  static PrinterProfile byName(String name) {
    return PrinterProfile.load(name)
        .orElseThrow(() -> new IllegalArgumentException("Unknown printer profile: " + name));
  }
}
