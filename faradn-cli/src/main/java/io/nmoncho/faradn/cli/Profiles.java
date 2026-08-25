package io.nmoncho.faradn.cli;

import io.nmoncho.faradn.printer.PrinterProfile;
import io.nmoncho.faradn.printer.TmT88vProfile;

/**
 * Resolves a printer profile by its CLI name.
 */
final class Profiles {

  private Profiles() {
  }

  static PrinterProfile byName(String name) {
    return switch (name.toLowerCase()) {
      case "tm-t88v", "tmt88v", "default" -> TmT88vProfile.INSTANCE;
      default -> throw new IllegalArgumentException("Unknown printer profile: " + name);
    };
  }
}
