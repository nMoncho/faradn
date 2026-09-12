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

  /**
   * Sends a raw request and reads up to {@code maxReplyBytes} of reply, within
   * the transport's status timeout. Returns the bytes actually read (possibly
   * fewer, or empty). This is the low-level primitive a
   * {@link StatusReader} drives for a language whose status poll is not the
   * ESC/POS {@code DLE EOT} of {@link #status()} (e.g. a StarPRNT
   * {@code ESC ACK SOH} ASB poll).
   * <p>
   * The default implementation reports no raw channel; a transport with a real
   * bidirectional link (USB, TCP) overrides it.
   *
   * @param request
   *        the request bytes to write
   * @param maxReplyBytes
   *        the most reply bytes to read
   * @return the reply bytes actually read
   * @throws TransportException
   *         if the exchange fails or the transport has no raw status channel
   */
  default byte[] exchange(byte[] request, int maxReplyBytes) {
    throw new TransportException("This transport has no raw status channel");
  }

  @Override
  void close();
}
