package io.nmoncho.faradn.document;

import java.util.ArrayList;
import java.util.List;

/**
 * A table laid out on a character grid. Rows hold cells; the renderer allocates
 * column widths within the printer's column budget and wraps each cell's
 * content to its column.
 */
public record Table(List<List<Cell>> rows) implements Block {

  public Table {
    if (rows == null || rows.isEmpty()) {
      throw new IllegalArgumentException("rows must not be null or empty");
    }
    final List<List<Cell>> copy = new ArrayList<>();
    for (List<Cell> row : rows) {
      if (row == null) {
        throw new IllegalArgumentException("row must not be null");
      }
      copy.add(List.copyOf(row));
    }
    rows = List.copyOf(copy);
  }
}
