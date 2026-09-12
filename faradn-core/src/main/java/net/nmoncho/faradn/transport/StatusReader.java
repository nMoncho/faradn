//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

/**
 * Reads a printer's readiness over a {@link Transport}, in whatever status
 * protocol its command language speaks. The two implementations mirror the
 * renderer split: {@link EscPosStatusReader} polls ESC/POS {@code DLE EOT},
 * {@link StarStatusReader} polls StarPRNT's ASB. Pick one by profile language
 * with {@link StatusReaders#forLanguage}.
 * <p>
 * A reader drives the transport's raw {@link Transport#exchange} primitive (or,
 * for ESC/POS, its {@link Transport#status()} shortcut) and decodes the reply
 * into the language-neutral {@link PrinterStatus}. Implementations are
 * stateless
 * and safe to share.
 */
public interface StatusReader {

  /**
   * Reads the printer's current status.
   *
   * @param transport
   *        the open connection to poll
   * @return the decoded status
   * @throws TransportException
   *         if the status cannot be read
   */
  PrinterStatus read(Transport transport);
}
