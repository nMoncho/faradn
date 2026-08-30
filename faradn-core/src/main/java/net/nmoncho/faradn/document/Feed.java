package net.nmoncho.faradn.document;

/**
 * Feeds blank lines of paper.
 */
public record Feed(int lines) implements Block {

  public Feed {
    if (lines < 1) {
      throw new IllegalArgumentException("lines must be >= 1, got " + lines);
    }
  }
}
