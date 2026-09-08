package net.nmoncho.faradn.document;

import java.util.List;

/**
 * A block of inline text runs printed as one or more lines.
 * <p>
 * Alignment is a block-level property (ESC/POS {@code ESC a} applies to
 * whole lines), so it lives here and not on the individual runs. An optional
 * {@link Border} draws a rule above ({@code top}) and/or below ({@code bottom})
 * the paragraph; the side borders are carried for later phases. An optional
 * {@link BlockLayout} indents the paragraph.
 * <p>
 * When {@code filled} is set the paragraph is a reverse-video section header:
 * each line is padded to the full paper width under invert ({@code GS B}) so
 * the
 * whole line is inked, with the label positioned by {@link #alignment()}. It is
 * derived from a dark CSS {@code background} on the block (see
 * {@link ComputedStyle}).
 */
public record Paragraph(List<TextRun> runs, ComputedStyle.Alignment alignment, Border border, BlockLayout layout,
    boolean filled) implements Block, Placeable {

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
    if (layout == null) {
      throw new IllegalArgumentException("layout must not be null");
    }
    runs = List.copyOf(runs);
  }

  /** A paragraph with no border and no indentation. */
  public Paragraph(List<TextRun> runs, ComputedStyle.Alignment alignment) {
    this(runs, alignment, Border.NONE, BlockLayout.NONE, false);
  }

  /** A paragraph with a border and no indentation. */
  public Paragraph(List<TextRun> runs, ComputedStyle.Alignment alignment, Border border) {
    this(runs, alignment, border, BlockLayout.NONE, false);
  }

  /** A paragraph with a border and indentation, not filled. */
  public Paragraph(List<TextRun> runs, ComputedStyle.Alignment alignment, Border border, BlockLayout layout) {
    this(runs, alignment, border, layout, false);
  }
}
