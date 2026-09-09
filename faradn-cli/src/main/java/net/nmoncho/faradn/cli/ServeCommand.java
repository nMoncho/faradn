//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.cli;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.ImagePolicy;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.transport.Transport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Runs the HTTP print server until the process is stopped.
 */
@Command(name = "serve", description = "Run an HTTP server that accepts print requests.")
final class ServeCommand implements Callable<Integer> {

  @Option(names = "--port", defaultValue = "8080", description = "port to listen on (default: ${DEFAULT-VALUE})")
  int port;

  @Option(names = "--bind", defaultValue = PrintServer.DEFAULT_BIND, description = "address to bind to (default: ${DEFAULT-VALUE}; use 0.0.0.0 to expose on all interfaces)")
  String bind;

  @Option(names = "--printer", paramLabel = "VID[:PID]", description = "default USB printer target")
  String printer;

  @Option(names = "--host", paramLabel = "HOST[:PORT]", description = "default network printer target")
  String host;

  @Option(names = "--profile", defaultValue = "tm-t88v", description = "printer profile (default: ${DEFAULT-VALUE})")
  String profile;

  @Option(names = "--allow-remote-images", description = "fetch <img src> from http(s)/file URLs (unsafe: the server renders untrusted HTML)")
  boolean allowRemoteImages;

  @Override
  public Integer call() throws Exception {
    // The server renders untrusted request bodies, so images are data:-only unless
    // the operator opts in.
    Image.policy(allowRemoteImages ? ImagePolicy.allowingRemote() : ImagePolicy.DATA_URIS_ONLY);

    final PrinterProfile prof = Profiles.byName(profile);
    final Supplier<Transport> transports = () -> Targets.open(printer, host);
    final PrintServer server = new PrintServer(bind, port, prof, transports);

    Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    server.start();

    final InetSocketAddress address = server.address();
    System.out.println("faradn listening on http://" + address.getHostString() + ":" + address.getPort());
    if (!allowRemoteImages) {
      System.out.println("remote <img src> fetching is disabled; pass --allow-remote-images to enable it");
    }
    if (!bind.equals(PrintServer.DEFAULT_BIND)) {
      System.out.println("warning: the server has no authentication; do not expose it to untrusted networks");
    }

    new CountDownLatch(1).await(); // block until the process is terminated
    return 0;
  }
}
