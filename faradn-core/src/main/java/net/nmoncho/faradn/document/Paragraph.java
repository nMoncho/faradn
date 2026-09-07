package net.nmoncho.faradn.document;

import java.util.List;

/**
 * A block of inline text runs printed as one or more lines.
 * <p>
 * Alignment is a block-level property (ESC/POS {@code ESC a} applies to
 * whole lines), so it lives here and not on the individual runs. An optional
 * {@link Border} draws a rule above ({@code top}) and/or below ({@code bottom})
 * the paragraph; the side borders are carried for later phases.
 */
public record Paragraph(List<TextRun> runs, ComputedStyle.Alignment alignment, Border border)
    implements
      Block,
      Placeable {

  public Paragraph {
    if (runs == null || runs.isEmpty()) {
      throw new IllegalArgumentException("runs must not be null or empty");
    }
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
    if (border == null) {
      throw new IllegalArgumentException("border must not be null");
    }
    runs = List.copyOf(runs);
  }

  /** A paragraph with no border. */
  public Paragraph(List<TextRun> runs, ComputedStyle.Alignment alignment) {
    this(runs, alignment, Border.NONE);
  }
}
