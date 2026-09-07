package net.nmoncho.faradn.document;

import java.util.ArrayList;
import java.util.List;

/**
 * A table laid out on a character grid. Rows hold cells; the renderer allocates
 * column widths within the printer's column budget and wraps each cell's
 * content to its column.
 * <p>
 * {@code outer} frames the table and {@code gridLines} draws separators between
 * cells, both with box-drawing characters (see {@link Border}). A borderless
 * table ({@link Border#NONE}, {@code gridLines = false}) renders with plain
 * space gutters, exactly as before.
 */
public record Table(List<List<Cell>> rows, Border outer, boolean gridLines) implements Block {

  public Table {
    if (rows == null || rows.isEmpty()) {
      throw new IllegalArgumentException("rows must not be null or empty");
    }
    if (outer == null) {
      throw new IllegalArgumentException("outer must not be null");
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

  /** A borderless table. */
  public Table(List<List<Cell>> rows) {
    this(rows, Border.NONE, false);
  }

  /** Whether the table draws any box-drawing lines (a frame or separators). */
  public boolean bordered() {
    return outer.any() || gridLines;
  }
}
