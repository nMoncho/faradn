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

  private static boolean bit(byte value, int index) {
    return (value & (1 << index)) != 0;
  }
}
