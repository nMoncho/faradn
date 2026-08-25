package io.nmoncho.faradn.document;

import java.util.List;

/**
 * A single table cell: its inline content (as styled runs) and how that content
 * is aligned within the cell's column. Content may be empty for a blank cell.
 */
public record Cell(List<TextRun> content, ComputedStyle.Alignment alignment) {

  public Cell {
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
    content = List.copyOf(content);
  }
}
