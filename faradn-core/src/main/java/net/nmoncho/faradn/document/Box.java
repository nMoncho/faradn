//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

import java.util.List;

/**
 * A border drawn around a group of stacked blocks - a bordered {@code <div>}
 * wrapping several paragraphs. Unlike a bordered {@link Paragraph} (a single
 * block), a {@code Box} nests other blocks, so the IR is no longer strictly
 * flat: the renderer recurses into {@code children} and frames them.
 * <p>
 * The renderer draws the frame with box-drawing characters (see
 * {@link Border});
 * paragraph children are wrapped inside the side rails, other block types
 * render
 * between the top and bottom edges.
 */
public record Box(Border border, List<Block> children) implements Block {

  public Box {
    if (border == null) {
      throw new IllegalArgumentException("border must not be null");
    }
    if (children == null || children.isEmpty()) {
      throw new IllegalArgumentException("children must not be null or empty");
    }
    children = List.copyOf(children);
  }
}
