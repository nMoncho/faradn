package io.nmoncho.faradn.document;

import java.util.List;

/**
 * A single table cell: its inline content (as styled runs), how that content is
 * aligned within the cell's column, and how many columns it spans. Content may
 * be empty for a blank cell; {@code colSpan} is at least&nbsp;1.
 */
public record Cell(List<TextRun> content, ComputedStyle.Alignment alignment, int colSpan) {

  public Cell {
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
    if (colSpan < 1) {
      throw new IllegalArgumentException("colSpan must be >= 1, got " + colSpan);
    }
    content = List.copyOf(content);
  }

  /** A cell spanning a single column. */
  public Cell(List<TextRun> content, ComputedStyle.Alignment alignment) {
    this(content, alignment, 1);
  }
}
