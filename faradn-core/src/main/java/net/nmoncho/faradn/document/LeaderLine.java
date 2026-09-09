//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

import java.util.List;

/**
 * A single line split into a {@code left} group and a {@code right} group with
 * the gap between them filled - the {@code Subtotal········9,00} receipt idiom.
 * <p>
 * Built from a {@code float: right} span: everything before/around it is the
 * left group, the span's content is the right group. The renderer measures both
 * in columns and fills the remaining width with {@code fill} (a space by
 * default,
 * a {@code .} for a dotted leader); if they don't fit on one line the right
 * group
 * drops to its own right-aligned line.
 */
public record LeaderLine(List<TextRun> left, List<TextRun> right, char fill) implements Block {

  public LeaderLine {
    if (left == null || right == null) {
      throw new IllegalArgumentException("left and right must not be null");
    }
    if (left.isEmpty() && right.isEmpty()) {
      throw new IllegalArgumentException("a leader line needs content on at least one side");
    }
    left = List.copyOf(left);
    right = List.copyOf(right);
  }
}
