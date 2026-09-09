//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

/**
 * Column-granular indentation for a block, all measured in character columns.
 * <p>
 * {@code leftIndent}/{@code rightIndent} pad and narrow every line;
 * {@code firstLineIndent} offsets only the first line (positive = a first-line
 * indent, negative = a hanging indent where the wrapped continuation lines sit
 * further in - which is how a list item's marker hangs). Indents map to spaces
 * on the monospace grid, so they are whole columns, not dots.
 */
public record BlockLayout(int leftIndent, int rightIndent, int firstLineIndent) {

  /** No indentation. */
  public static final BlockLayout NONE = new BlockLayout(0, 0, 0);

  public BlockLayout {
    if (leftIndent < 0 || rightIndent < 0) {
      throw new IllegalArgumentException("leftIndent/rightIndent must be >= 0");
    }
  }

  /** Whether this layout indents anything. */
  public boolean isNone() {
    return leftIndent == 0 && rightIndent == 0 && firstLineIndent == 0;
  }
}
