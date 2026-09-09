//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.cli;

import net.nmoncho.faradn.printer.PrinterProfile;

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
