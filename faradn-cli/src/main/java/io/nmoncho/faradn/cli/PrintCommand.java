package io.nmoncho.faradn.cli;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import io.nmoncho.faradn.Document;
import io.nmoncho.faradn.Printer;
import io.nmoncho.faradn.printer.PrinterProfile;
import io.nmoncho.faradn.printer.escpos.EscPosRenderer;
import io.nmoncho.faradn.transport.Transport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Renders HTML files and prints them, or (with {@code --dry-run}) writes the
 * rendered ESC/POS bytes to standard output.
 */
@Command(name = "print", description = "Render and print one or more HTML documents.")
final class PrintCommand implements Callable<Integer> {

  @Parameters(paramLabel = "FILE", arity = "1..*", description = "HTML files to print")
  List<File> files;

  @Option(names = "--printer", paramLabel = "VID[:PID]", description = "USB printer, hex vendor and optional product id (e.g. 0x04b8)")
  String printer;

  @Option(names = "--host", paramLabel = "HOST[:PORT]", description = "network printer address, raw TCP (default port 9100)")
  String host;

  @Option(names = "--profile", defaultValue = "tm-t88v", description = "printer profile (default: ${DEFAULT-VALUE})")
  String profile;

  @Option(names = "--copies", defaultValue = "1", description = "number of copies (default: ${DEFAULT-VALUE})")
  int copies;

  @Option(names = "--dry-run", description = "render and write the ESC/POS bytes to stdout instead of printing")
  boolean dryRun;

  @Override
  public Integer call() {
    try {
      final PrinterProfile prof = Profiles.byName(profile);
      for (File file : files) {
        final Document document = Document.from(file);
        if (dryRun) {
          final byte[] payload = new EscPosRenderer(prof).render(document.blocks());
          for (int i = 0; i < copies; i++) {
            System.out.write(payload);
          }
          System.out.flush();
        } else {
          try (Transport transport = Targets.open(printer, host)) {
            for (int i = 0; i < copies; i++) {
              Printer.print(transport, document, prof);
            }
          }
        }
      }
      return 0;
    } catch (IllegalArgumentException e) {
      System.err.println("faradn: " + e.getMessage());
      return 2;
    } catch (IOException | RuntimeException e) {
      System.err.println("faradn: " + e.getMessage());
      return 1;
    }
  }
}
