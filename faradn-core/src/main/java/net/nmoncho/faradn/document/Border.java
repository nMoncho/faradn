//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

/**
 * Which sides of an element have a border, and its line weight.
 * <p>
 * ESC/POS standard mode has no geometric line command, so borders are drawn
 * with
 * box-drawing characters on the monospace grid; only a single ({@code ─}) or
 * double ({@code ═}) weight is expressible, and colour, radius, and per-side
 * pixel widths do not map. The same descriptor is forward-compatible with label
 * languages that do have native rectangles (see {@code PLAN_BORDERS.md}).
 */
public record Border(boolean top, boolean right, boolean bottom, boolean left, Style style) {

  /** Line weight: single-line or double-line box glyphs. */
  public enum Style {
    SINGLE, DOUBLE
  }

  /** No border on any side. */
  public static final Border NONE = new Border(false, false, false, false, Style.SINGLE);

  public Border {
    if (style == null) {
      throw new IllegalArgumentException("style must not be null");
    }
  }

  /** A border on all four sides at the given weight. */
  public static Border all(Style style) {
    return new Border(true, true, true, true, style);
  }

  /** Whether any side has a border. */
  public boolean any() {
    return top || right || bottom || left;
  }
}
