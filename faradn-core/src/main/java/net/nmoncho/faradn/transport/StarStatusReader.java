//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

import net.nmoncho.faradn.printer.starprnt.commands.StarStatusCommands;

/**
 * Reads StarPRNT readiness by polling the printer for its Automatic Status Back
 * (ASB) block with {@code ESC ACK SOH} and decoding it via
 * {@link PrinterStatus#ofStarAsb(byte[])}. StarPRNT has no ESC/POS
 * {@code DLE EOT}, so this reader drives the transport's raw
 * {@link Transport#exchange} primitive instead.
 * <p>
 * <strong>Optimistic by design:</strong> if the poll returns no usable block,
 * it
 * reports {@link PrinterStatus#READY} rather than refusing a job, so an
 * unexpected reply can never <em>falsely</em> block printing. A block that
 * decodes to a definite problem (cover open, paper out, …) is still reported.
 * <p>
 * <strong>Hardware-verification note:</strong> the ASB bit map is read from the
 * spec but not yet confirmed on paper, and over TCP&nbsp;9100 the reply may
 * carry
 * extra framing ahead of the block. The reader assumes the block begins at the
 * start of the reply (true for USB); the gated Star hardware test captures a
 * real
 * reply to finalize the offset. This is why live Star status stays gated off
 * (see {@code PrinterLanguage.supportsRealtimeStatus}) until confirmed.
 */
public final class StarStatusReader implements StatusReader {

  /** Enough to hold the 15-byte ASB block plus any transport framing. */
  private static final int MAX_REPLY_BYTES = 64;

  /**
   * The fewest reply bytes that carry a decodable status (through the paper
   * sensor).
   */
  private static final int MIN_BLOCK_BYTES = 6;

  @Override
  public PrinterStatus read(Transport transport) {
    final byte[] reply = transport.exchange(StarStatusCommands.POLL_ASB.getCode(), MAX_REPLY_BYTES);
    if (reply == null || reply.length < MIN_BLOCK_BYTES) {
      return PrinterStatus.READY; // no usable block -> never false-refuse
    }
    return PrinterStatus.ofStarAsb(reply);
  }
}
