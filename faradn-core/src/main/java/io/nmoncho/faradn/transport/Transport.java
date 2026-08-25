package io.nmoncho.faradn.transport;

/**
 * A byte sink to a printer, plus its real-time status channel. The delivery
 * mechanism — USB, a network socket, an in-memory buffer — is hidden behind
 * this interface so the rest of the library never depends on how bytes reach
 * paper.
 * <p>
 * Transports own I/O resources and are {@link AutoCloseable}; use them with
 * try-with-resources. {@link #close()} narrows away the checked exception so
 * callers do not have to catch it.
 */
public interface Transport extends AutoCloseable {

  /**
   * Sends a rendered ESC/POS payload to the printer.
   *
   * @param payload
   *        the bytes to send
   */
  void write(byte[] payload);

  /**
   * Queries the printer's real-time status ({@code DLE EOT}).
   *
   * @return the decoded status
   */
  PrinterStatus status();

  @Override
  void close();
}
