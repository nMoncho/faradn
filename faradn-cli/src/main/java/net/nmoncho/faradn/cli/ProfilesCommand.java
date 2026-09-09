//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.cli;

import java.util.List;
import java.util.concurrent.Callable;

import net.nmoncho.faradn.printer.PrinterProfile;

import picocli.CommandLine.Command;

/**
 * Lists the printer profiles available in the bundled capability database, i.e.
 * the names accepted by {@code print --profile}.
 */
@Command(name = "profiles", description = "List the available printer profiles.")
final class ProfilesCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    final List<String> profiles = PrinterProfile.available();
    if (profiles.isEmpty()) {
      System.out.println("No printer profiles available.");
      return 0;
    }

    System.out.println(profiles.size() + " printer profiles:");
    profiles.forEach(name -> System.out.println("  " + name));
    return 0;
  }
}
