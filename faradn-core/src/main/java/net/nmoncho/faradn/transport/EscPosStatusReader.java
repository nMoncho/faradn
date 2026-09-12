//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

/**
 * Reads ESC/POS readiness via the real-time {@code DLE EOT 1..4} poll. The
 * query
 * and decode already live on the transport's {@link Transport#status()}, so
 * this
 * reader is a thin adapter that keeps the ESC/POS path byte-for-byte unchanged
 * while fitting the pluggable {@link StatusReader} seam.
 */
public final class EscPosStatusReader implements StatusReader {

  @Override
  public PrinterStatus read(Transport transport) {
    return transport.status();
  }
}
