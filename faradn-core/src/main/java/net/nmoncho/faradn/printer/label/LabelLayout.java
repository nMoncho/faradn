//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.label;

import java.util.ArrayList;
import java.util.List;

import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.text.TextWrapper;

/**
 * Pure layout arithmetic shared by the positioned-label renderers (ZPL, EPL).
 * All positions are integer dots at the profile dpi, matching the
 * {@code Canvas}/{@code Placement} IR. Nothing here emits language bytes, so
 * both
 * label backends reuse it verbatim (the label-side analogue of
 * {@link net.nmoncho.faradn.printer.text.TextWrapper}).
 * <p>
 * Three responsibilities:
 * <ul>
 * <li><strong>Rotation</strong> ({@link #rotate}): map an upright placement
 * position into the output frame for a whole-canvas direction. Neither ZPL nor
 * EPL has a command that rotates the whole page 90/270 degrees, so the renderer
 * transforms the coordinates itself and sets each field's own orientation.</li>
 * <li><strong>Dot-to-column bridge</strong> ({@link #charWidthDots},
 * {@link #columnsForWidth}): a label has no character grid, so a placement's
 * wrap width in dots is converted to a column budget for {@code TextWrapper}
 * using the native font's char width in dots.</li>
 * <li><strong>Field segmentation</strong> ({@link #segment}): a single label
 * text field renders one font/size/style, so a mixed-style {@code Paragraph} is
 * laid out into one positioned field per run per wrapped line.</li>
 * </ul>
 */
public final class LabelLayout {

  private LabelLayout() {
  }

  /** A position in printer dots. */
  public record Point(int xDots, int yDots) {
  }

  /**
   * One positioned text field: a single-style {@link TextRun} offset by
   * {@code (dxDots, dyDots)} from its placement's origin. A label language emits
   * one field per segment (ZPL {@code ^FO}+{@code ^A}+{@code ^FD}, EPL
   * {@code A}), because a single field renders one font, size, and style.
   */
  public record TextSegment(int dxDots, int dyDots, TextRun run) {
  }

  /**
   * Maps an upright top-left position {@code (xDots, yDots)} in a canvas of
   * {@code canvasWidthDots} by {@code canvasHeightDots} into the output frame for
   * a whole-canvas {@code direction}, anchoring each rotation at the corner that
   * becomes the new origin. Generalized from the ESC/POS page-mode transform
   * (which the receipt backend hardware-verified), without the baseline drop that
   * is specific to ESC/POS cursor-flow and unnecessary for label languages whose
   * field origins are already top-left.
   * <ul>
   * <li>{@code NORMAL}: {@code (x, y)}.</li>
   * <li>{@code ROTATE_90_CW}: {@code (y, w - x)}.</li>
   * <li>{@code ROTATE_90_CCW}: {@code (h - y, x)}.</li>
   * <li>{@code ROTATE_180}: {@code (w - x, h - y)}.</li>
   * </ul>
   * A label renderer pairs this origin with the field's own orientation letter
   * (ZPL {@code N}/{@code R}/{@code I}/{@code B}) or rotation parameter (EPL
   * {@code 0}/{@code 1}/{@code 2}/{@code 3}). The exact on-paper anchor under a
   * rotated field is confirmed by the gated Zebra hardware fixture (see
   * {@code PLAN_ZEBRA_ZD421.md} Sections 5 and 10); the arithmetic here is the
   * working mapping and is unit-tested per direction. Coordinates that would go
   * negative (a point outside the canvas) are clamped to 0.
   *
   * @param xDots
   *        upright x, from the canvas top-left
   * @param yDots
   *        upright y, from the canvas top-left
   * @param canvasWidthDots
   *        the upright canvas width
   * @param canvasHeightDots
   *        the upright canvas height
   * @param direction
   *        the whole-canvas rotation
   * @return the field origin in the rotated output frame
   */
  public static Point rotate(int xDots, int yDots, int canvasWidthDots, int canvasHeightDots,
      Canvas.Direction direction) {
    if (direction == null) {
      throw new IllegalArgumentException("direction must not be null");
    }
    return switch (direction) {
      case NORMAL -> new Point(xDots, yDots);
      case ROTATE_90_CW -> new Point(yDots, Math.max(0, canvasWidthDots - xDots));
      case ROTATE_90_CCW -> new Point(Math.max(0, canvasHeightDots - yDots), xDots);
      case ROTATE_180 -> new Point(Math.max(0, canvasWidthDots - xDots), Math.max(0, canvasHeightDots - yDots));
    };
  }

  /**
   * The width in dots of one base character cell for a native font, derived as
   * {@code dotsPerLine / fontColumns} (the same derivation the ESC/POS page-mode
   * path uses). Labels position by dots, so this bridges the profile's
   * receipt-style {@code columns} to a dot measurement for wrapping and
   * per-run offsets.
   *
   * @param dotsPerLine
   *        the profile's printable width in dots
   * @param fontColumns
   *        the font's characters-per-line budget
   * @return the char cell width in dots, at least 1
   */
  public static int charWidthDots(int dotsPerLine, int fontColumns) {
    if (dotsPerLine < 1) {
      throw new IllegalArgumentException("dotsPerLine must be >= 1, got " + dotsPerLine);
    }
    if (fontColumns < 1) {
      throw new IllegalArgumentException("fontColumns must be >= 1, got " + fontColumns);
    }
    return Math.max(1, Math.round((float) dotsPerLine / fontColumns));
  }

  /**
   * The column budget for a placement of a given width in dots, for feeding
   * {@link #segment}.
   *
   * @param widthDots
   *        the placement's wrap width in dots
   * @param charWidthDots
   *        the font char width from {@link #charWidthDots}
   * @return the number of columns that fit, at least 1
   */
  public static int columnsForWidth(int widthDots, int charWidthDots) {
    if (charWidthDots < 1) {
      throw new IllegalArgumentException("charWidthDots must be >= 1, got " + charWidthDots);
    }
    return Math.max(1, widthDots / charWidthDots);
  }

  /**
   * Lays a paragraph's runs out into positioned single-style fields. Runs are
   * word-wrapped to {@code wrapColumns} via {@link TextWrapper} (pass
   * {@code wrapColumns <= 0} to keep everything on one line); then each wrapped
   * line's runs are placed left to right, offset in x by the accumulated column
   * width of the runs before it and in y by the line index times
   * {@code lineHeightDots}.
   *
   * @param runs
   *        the paragraph's styled runs
   * @param wrapColumns
   *        the wrap budget in columns, or {@code <= 0} to not wrap
   * @param charWidthDots
   *        the font char width from {@link #charWidthDots}, for x offsets
   * @param lineHeightDots
   *        the line advance in dots, for y offsets
   * @return one segment per run per line, in reading order
   */
  public static List<TextSegment> segment(List<TextRun> runs, int wrapColumns, int charWidthDots,
      int lineHeightDots) {
    if (runs == null) {
      throw new IllegalArgumentException("runs must not be null");
    }
    if (charWidthDots < 1) {
      throw new IllegalArgumentException("charWidthDots must be >= 1, got " + charWidthDots);
    }
    if (lineHeightDots < 1) {
      throw new IllegalArgumentException("lineHeightDots must be >= 1, got " + lineHeightDots);
    }
    final List<List<TextRun>> lines = wrapColumns > 0 ? TextWrapper.wrap(runs, wrapColumns) : List.of(runs);
    final List<TextSegment> segments = new ArrayList<>();
    for (int line = 0; line < lines.size(); line++) {
      final int dy = line * lineHeightDots;
      int columns = 0;
      for (TextRun run : lines.get(line)) {
        segments.add(new TextSegment(columns * charWidthDots, dy, run));
        columns += runColumns(run);
      }
    }
    return segments;
  }

  /** A run's width in columns: one column per char, times its width multiple. */
  private static int runColumns(TextRun run) {
    return run.text().length() * run.style().widthMultiple();
  }
}
