//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Entry point for the {@code faradn} command-line interface: list printers,
 * print HTML files, or serve print requests over HTTP.
 */
@Command(name = "faradn", mixinStandardHelpOptions = true, version = "faradn "
    + BuildInfo.VERSION, description = "Print HTML documents on ESC/POS printers.", subcommands = {
        ListCommand.class, PrintCommand.class, ServeCommand.class, ProfilesCommand.class })
public final class Faradn implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new Faradn()).execute(args));
  }
}
