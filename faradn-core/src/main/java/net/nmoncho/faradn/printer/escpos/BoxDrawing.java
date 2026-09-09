//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos;

import net.nmoncho.faradn.document.Border;

/**
 * The box-drawing glyphs for one line weight, used to draw borders on the
 * character grid. All are present in PC437/PC850/PC858, which the code-page
 * encoder switches to automatically.
 *
 * @param horizontal
 *        horizontal line ({@code ─}/{@code ═})
 * @param vertical
 *        vertical line ({@code │}/{@code ║})
 * @param topLeft
 *        top-left corner ({@code ┌}/{@code ╔})
 * @param topRight
 *        top-right corner ({@code ┐}/{@code ╗})
 * @param bottomLeft
 *        bottom-left corner ({@code └}/{@code ╚})
 * @param bottomRight
 *        bottom-right corner ({@code ┘}/{@code ╝})
 * @param teeDown
 *        top edge join, branch downward ({@code ┬}/{@code ╦})
 * @param teeUp
 *        bottom edge join, branch upward ({@code ┴}/{@code ╩})
 * @param teeRight
 *        left edge join, branch rightward ({@code ├}/{@code ╠})
 * @param teeLeft
 *        right edge join, branch leftward ({@code ┤}/{@code ╣})
 * @param cross
 *        interior four-way join ({@code ┼}/{@code ╬})
 */
public record BoxDrawing(String horizontal, String vertical, String topLeft, String topRight,
    String bottomLeft, String bottomRight, String teeDown, String teeUp, String teeRight,
    String teeLeft, String cross) {

  public static final BoxDrawing SINGLE = new BoxDrawing(
      "─", "│", "┌", "┐", "└", "┘",
      "┬", "┴", "├", "┤", "┼");

  public static final BoxDrawing DOUBLE = new BoxDrawing(
      "═", "║", "╔", "╗", "╚", "╝",
      "╦", "╩", "╠", "╣", "╬");

  /** The glyph set for a border style. */
  public static BoxDrawing of(Border.Style style) {
    return style == Border.Style.DOUBLE ? DOUBLE : SINGLE;
  }
}
