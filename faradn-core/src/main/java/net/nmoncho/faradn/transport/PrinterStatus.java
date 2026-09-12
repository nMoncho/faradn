//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

/**
 * Decoded real-time printer status. Fields are derived from the four
 * {@code DLE EOT} response bytes; see {@link #of}.
 */
public record PrinterStatus(boolean online, boolean coverOpen, boolean paperEnd, boolean paperNearEnd,
    boolean error) {

  /** A printer with nothing wrong: online, covered, paper present, no error. */
  public static final PrinterStatus READY = new PrinterStatus(true, false, false, false, false);

  /** Whether the printer can accept a job right now. */
  public boolean ready() {
    return online && !coverOpen && !paperEnd && !error;
  }

  /**
   * Decodes the four real-time status bytes returned by {@code DLE EOT 1..4}.
   *
   * @param printer
   *        reply to {@code DLE EOT 1} (printer status)
   * @param offline
   *        reply to {@code DLE EOT 2} (offline cause)
   * @param error
   *        reply to {@code DLE EOT 3} (error cause)
   * @param paper
   *        reply to {@code DLE EOT 4} (paper roll sensor)
   * @return the decoded status
   */
  public static PrinterStatus of(byte printer, byte offline, byte error, byte paper) {
    boolean online = !bit(printer, 3); // DLE EOT 1 bit 3: 1 = offline
    boolean coverOpen = bit(offline, 2); // DLE EOT 2 bit 2: 1 = cover open
    boolean paperEndStop = bit(offline, 5); // DLE EOT 2 bit 5: stopped on paper end
    boolean anyError = bit(error, 3) || bit(error, 5) || bit(error, 6); // DLE EOT 3
    boolean paperOut = bit(paper, 5) && bit(paper, 6); // DLE EOT 4 bits 5,6: paper end
    boolean nearEnd = bit(paper, 2) && bit(paper, 3); // DLE EOT 4 bits 2,3: near end
    return new PrinterStatus(online, coverOpen, paperEndStop || paperOut, nearEnd, anyError);
  }

  /**
   * Decodes a StarPRNT Automatic Status Back (ASB) v6 block into the same five
   * booleans. The printer-status bytes start at the block's third byte (bytes 1-2
   * are the ASB headers), so with a 0-indexed {@code block}:
   * <ul>
   * <li>byte 3 ({@code block[2]}) - printer status: bit&nbsp;3 online/offline,
   * bit&nbsp;5 cover open;</li>
   * <li>byte 4 ({@code block[3]}) - error information: bit&nbsp;2 mechanical,
   * bit&nbsp;3 auto-cutter, bit&nbsp;5 non-recoverable;</li>
   * <li>byte 6 ({@code block[5]}) - paper sensor: bit&nbsp;3 paper end;</li>
   * <li>byte 7 ({@code block[6]}) - paper-hold sensor: bit&nbsp;1 (the base
   * TSP100IV's near-end proxy, as it does not implement the byte-6 near-end
   * bit).</li>
   * </ul>
   * Verified against the StarPRNT Command Specifications (Rev 4.20), Automatic
   * Status appendix pp217-233.
   *
   * @param block
   *        the ASB status block, starting at its Header-1 byte
   * @return the decoded status
   * @throws IllegalArgumentException
   *         if the block is too short to read the status bytes
   */
  public static PrinterStatus ofStarAsb(byte[] block) {
    if (block == null || block.length < 6) {
      throw new IllegalArgumentException("Star ASB block too short: " + (block == null ? "null" : block.length));
    }
    boolean online = !bit(block[2], 3); // 3rd byte bit 3: 1 = offline
    boolean coverOpen = bit(block[2], 5); // 3rd byte bit 5: 1 = cover open
    boolean anyError = bit(block[3], 2) || bit(block[3], 3) || bit(block[3], 5); // mechanical / cutter / non-recoverable
    boolean paperEnd = bit(block[5], 3); // 6th byte bit 3: 1 = paper end
    boolean nearEnd = block.length > 6 && bit(block[6], 1); // 7th byte bit 1: paper-hold sensor (near-end proxy)
    return new PrinterStatus(online, coverOpen, paperEnd, nearEnd, anyError);
  }

  private static boolean bit(byte value, int index) {
    return (value & (1 << index)) != 0;
  }
}
