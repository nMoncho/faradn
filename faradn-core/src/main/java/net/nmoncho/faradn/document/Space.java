//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

/**
 * Vertical blank space between blocks, in dots - a block-level margin. The
 * renderer feeds the paper by exactly this many dots ({@code ESC J}), finer
 * than
 * the line-granular {@link Feed}. Built from CSS {@code margin-top}/
 * {@code margin-bottom} on a block.
 */
public record Space(int dots) implements Block {

  public Space {
    if (dots <= 0) {
      throw new IllegalArgumentException("dots must be > 0, got " + dots);
    }
  }
}
