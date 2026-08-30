package net.nmoncho.faradn.cli;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

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

  @Option(names = "--printer", paramLabel = "VID[:PID]", description = "default USB printer target")
  String printer;

  @Option(names = "--host", paramLabel = "HOST[:PORT]", description = "default network printer target")
  String host;

  @Option(names = "--profile", defaultValue = "tm-t88v", description = "printer profile (default: ${DEFAULT-VALUE})")
  String profile;

  @Override
  public Integer call() throws Exception {
    final PrinterProfile prof = Profiles.byName(profile);
    final Supplier<Transport> transports = () -> Targets.open(printer, host);
    final PrintServer server = new PrintServer(port, prof, transports);

    Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    server.start();
    System.out.println("faradn listening on http://localhost:" + server.port());

    new CountDownLatch(1).await(); // block until the process is terminated
    return 0;
  }
}
