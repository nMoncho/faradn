//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

/**
 * A byte sink to a printer, plus its real-time status channel. The delivery
 * mechanism - USB, a network socket, an in-memory buffer - is hidden behind
 * this interface so the rest of the library never depends on how bytes reach
 * paper.
 * <p>
 * Transports own I/O resources and are {@link AutoCloseable}; use them with
 * try-with-resources. {@link #close()} narrows away the checked exception so
 * callers do not have to catch it.
 * <p>
 * <strong>Thread-safety:</strong> a transport represents a single connection to
 * one printer and is <em>not</em> safe for concurrent use. Send one job at a
 * time; the printer serializes work anyway.
 */
public interface Transport extends AutoCloseable {

  /**
   * Sends a rendered ESC/POS payload to the printer.
   *
   * @param payload
   *        the bytes to send
   * @throws TransportException
   *         if the bytes cannot be written to the printer
   */
  void write(byte[] payload);

  /**
   * Queries the printer's real-time status ({@code DLE EOT}).
   *
   * @return the decoded status
   * @throws TransportException
   *         if the status cannot be read (for example the printer has no status
   *         channel, or the read times out)
   */
  PrinterStatus status();

  @Override
  void close();
}
