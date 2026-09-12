//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.label;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.label.LabelLayout.Point;
import net.nmoncho.faradn.printer.label.LabelLayout.TextSegment;

class LabelLayoutTest {

  private static final ComputedStyle PLAIN = ComputedStyle.INITIAL;
  private static final ComputedStyle BOLD = new ComputedStyle(true, false, 1, 1, Alignment.LEFT, false);
  private static final ComputedStyle WIDE = new ComputedStyle(false, false, 2, 1, Alignment.LEFT, false);

  // Canvas is 800 x 400 dots for the rotation cases.

  @Test
  void rotateNormalIsIdentity() {
    final Point p = LabelLayout.rotate(100, 50, 800, 400, Canvas.Direction.NORMAL);
    assertEquals(100, p.xDots());
    assertEquals(50, p.yDots());
  }

  @Test
  void rotate90CwAnchorsTopRight() {
    // (x, y) -> (y, w - x)
    final Point p = LabelLayout.rotate(100, 50, 800, 400, Canvas.Direction.ROTATE_90_CW);
    assertEquals(50, p.xDots());
    assertEquals(700, p.yDots());
  }

  @Test
  void rotate90CcwAnchorsBottomLeft() {
    // (x, y) -> (h - y, x)
    final Point p = LabelLayout.rotate(100, 50, 800, 400, Canvas.Direction.ROTATE_90_CCW);
    assertEquals(350, p.xDots());
    assertEquals(100, p.yDots());
  }

  @Test
  void rotate180AnchorsBottomRight() {
    // (x, y) -> (w - x, h - y)
    final Point p = LabelLayout.rotate(100, 50, 800, 400, Canvas.Direction.ROTATE_180);
    assertEquals(700, p.xDots());
    assertEquals(350, p.yDots());
  }

  @Test
  void rotateClampsOutOfBoundsToZero() {
    // A point past the right edge must not produce a negative coordinate.
    final Point p = LabelLayout.rotate(900, 50, 800, 400, Canvas.Direction.ROTATE_90_CW);
    assertEquals(50, p.xDots());
    assertEquals(0, p.yDots());
  }

  @Test
  void rotateRejectsNullDirection() {
    assertThrows(IllegalArgumentException.class, () -> LabelLayout.rotate(0, 0, 800, 400, null));
  }

  @Test
  void charWidthDotsDividesPrintWidthByColumns() {
    assertEquals(20, LabelLayout.charWidthDots(800, 40));
    assertEquals(20, LabelLayout.charWidthDots(832, 42)); // 19.8 rounds to 20
    assertEquals(1, LabelLayout.charWidthDots(5, 100)); // never below 1
  }

  @Test
  void charWidthDotsValidatesArgs() {
    assertThrows(IllegalArgumentException.class, () -> LabelLayout.charWidthDots(0, 40));
    assertThrows(IllegalArgumentException.class, () -> LabelLayout.charWidthDots(800, 0));
  }

  @Test
  void columnsForWidthDividesWidthByCharWidth() {
    assertEquals(20, LabelLayout.columnsForWidth(400, 20));
    assertEquals(1, LabelLayout.columnsForWidth(10, 20)); // never below 1
  }

  @Test
  void columnsForWidthValidatesArgs() {
    assertThrows(IllegalArgumentException.class, () -> LabelLayout.columnsForWidth(400, 0));
  }

  @Test
  void segmentSingleRunNoWrap() {
    final List<TextSegment> segs = LabelLayout.segment(List.of(new TextRun("HELLO", PLAIN)), 0, 10, 24);
    assertEquals(1, segs.size());
    assertEquals(0, segs.get(0).dxDots());
    assertEquals(0, segs.get(0).dyDots());
    assertEquals("HELLO", segs.get(0).run().text());
  }

  @Test
  void segmentWrapsToMultipleLines() {
    // "HELLO WORLD" at 5 columns wraps at the space; the breaking space is dropped.
    final List<TextSegment> segs = LabelLayout.segment(List.of(new TextRun("HELLO WORLD", PLAIN)), 5, 10, 24);
    assertEquals(2, segs.size());
    assertEquals(0, segs.get(0).dxDots());
    assertEquals(0, segs.get(0).dyDots());
    assertEquals("HELLO", segs.get(0).run().text());
    assertEquals(0, segs.get(1).dxDots());
    assertEquals(24, segs.get(1).dyDots()); // second line advanced by lineHeight
    assertEquals("WORLD", segs.get(1).run().text());
  }

  @Test
  void segmentOffsetsMixedStyleRunsOnOneLine() {
    // No wrap: two runs on one line; the second starts after the first's width.
    final List<TextSegment> segs = LabelLayout.segment(List.of(new TextRun("AB", PLAIN), new TextRun("CD", BOLD)), 0,
        10, 24);
    assertEquals(2, segs.size());
    assertEquals(0, segs.get(0).dxDots());
    assertEquals(20, segs.get(1).dxDots()); // 2 chars x 10 dots
    assertEquals(BOLD, segs.get(1).run().style());
  }

  @Test
  void segmentCountsWidthMultipleInTheOffset() {
    // A double-width run occupies two columns per char, pushing the next run out.
    final List<TextSegment> segs = LabelLayout.segment(List.of(new TextRun("AB", WIDE), new TextRun("CD", PLAIN)), 0,
        10, 24);
    assertEquals(40, segs.get(1).dxDots()); // 2 chars x 2 (wide) x 10 dots
    assertEquals("CD", segs.get(1).run().text());
  }

  @Test
  void segmentEmptyRunsIsEmpty() {
    assertTrue(LabelLayout.segment(List.of(), 0, 10, 24).isEmpty());
  }

  @Test
  void segmentValidatesArgs() {
    assertThrows(IllegalArgumentException.class, () -> LabelLayout.segment(null, 0, 10, 24));
    assertThrows(IllegalArgumentException.class,
        () -> LabelLayout.segment(List.of(new TextRun("X", PLAIN)), 0, 0, 24));
    assertThrows(IllegalArgumentException.class,
        () -> LabelLayout.segment(List.of(new TextRun("X", PLAIN)), 0, 10, 0));
  }
}
