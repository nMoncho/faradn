package net.nmoncho.faradn.transport;

import net.nmoncho.faradn.PrintingException;

/**
 * Thrown when a pre-flight check finds the printer cannot accept a job
 * (offline, cover open, out of paper, or in an error state).
 * Carries the {@link PrinterStatus} that caused the refusal.
 */
public class PrinterNotReadyException extends PrintingException {

  private final PrinterStatus status;

  public PrinterNotReadyException(PrinterStatus status) {
    super("Printer is not ready to print: " + status);
    this.status = status;
  }

  public PrinterStatus status() {
    return status;
  }
}
