package io.nmoncho.faradn.document;

import java.util.List;

/**
 * A block of inline text runs printed as one or more lines.
 * <p>
 * Alignment is a block-level property (ESC/POS {@code ESC a} applies to
 * whole lines), so it lives here and not on the individual runs.
 */
public record Paragraph(List<TextRun> runs, ComputedStyle.Alignment alignment) implements Block {

  public Paragraph {
    if (runs == null || runs.isEmpty()) {
      throw new IllegalArgumentException("runs must not be null or empty");
    }
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
    runs = List.copyOf(runs);
  }
}
