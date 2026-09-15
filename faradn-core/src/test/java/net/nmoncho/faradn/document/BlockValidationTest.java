//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;

public class BlockValidationTest {

  private static final TextRun RUN = new TextRun("text", ComputedStyle.INITIAL);

  @Test
  void textRunRejectsMissingText() {
    assertThrows(IllegalArgumentException.class, () -> new TextRun(null, ComputedStyle.INITIAL));
    assertThrows(IllegalArgumentException.class, () -> new TextRun("", ComputedStyle.INITIAL));
  }

  @Test
  void textRunRejectsMissingStyle() {
    assertThrows(IllegalArgumentException.class, () -> new TextRun("text", null));
  }

  @Test
  void paragraphRejectsMissingRuns() {
    assertThrows(IllegalArgumentException.class, () -> new Paragraph(null, Alignment.LEFT));
    assertThrows(IllegalArgumentException.class, () -> new Paragraph(List.of(), Alignment.LEFT));
  }

  @Test
  void paragraphRejectsMissingAlignment() {
    assertThrows(IllegalArgumentException.class, () -> new Paragraph(List.of(RUN), null));
  }

  @Test
  void paragraphRejectsMissingBorder() {
    assertThrows(IllegalArgumentException.class, () -> new Paragraph(List.of(RUN), Alignment.LEFT, null));
  }

  @Test
  void boxRejectsNullBorderAndEmptyChildren() {
    final Paragraph child = new Paragraph(List.of(RUN), Alignment.LEFT);
    assertThrows(IllegalArgumentException.class, () -> new Box(null, List.of(child)));
    assertThrows(IllegalArgumentException.class, () -> new Box(Border.all(Border.Style.SINGLE), List.of()));
  }

  @Test
  void leaderLineRejectsNullsAndBothSidesEmpty() {
    assertThrows(IllegalArgumentException.class, () -> new LeaderLine(null, List.of(RUN), ' '));
    assertThrows(IllegalArgumentException.class, () -> new LeaderLine(List.of(RUN), null, ' '));
    assertThrows(IllegalArgumentException.class, () -> new LeaderLine(List.of(), List.of(), ' '));
  }

  @Test
  void blockLayoutRejectsNegativeSideIndentsButAllowsNegativeFirstLine() {
    assertThrows(IllegalArgumentException.class, () -> new BlockLayout(-1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new BlockLayout(0, -1, 0));
    assertEquals(-2, new BlockLayout(0, 0, -2).firstLineIndent()); // hanging indent
    assertTrue(BlockLayout.NONE.isNone());
    assertFalse(new BlockLayout(1, 0, 0).isNone());
  }

  @Test
  void spaceRejectsNonPositiveDots() {
    assertThrows(IllegalArgumentException.class, () -> new Space(0));
    assertThrows(IllegalArgumentException.class, () -> new Space(-5));
    assertEquals(10, new Space(10).dots());
  }

  @Test
  void paragraphCopiesItsRuns() {
    final List<TextRun> runs = new ArrayList<>();
    runs.add(RUN);

    final Paragraph p = new Paragraph(runs, Alignment.LEFT);
    runs.add(new TextRun("later", ComputedStyle.INITIAL));

    assertEquals(1, p.runs().size());
    assertThrows(UnsupportedOperationException.class, () -> p.runs().add(RUN));
  }

  @Test
  void imageBlockRejectsMissingParts() {
    final Image image = Image.fromUrl("http://example.com/logo.png");

    assertThrows(IllegalArgumentException.class, () -> new ImageBlock(null, Alignment.LEFT));
    assertThrows(IllegalArgumentException.class, () -> new ImageBlock(image, null));
  }

  @Test
  void barcodeRejectsMissingData() {
    assertThrows(IllegalArgumentException.class, () -> new Barcode(null, "code128", Alignment.LEFT));
    assertThrows(IllegalArgumentException.class, () -> new Barcode("", "code128", Alignment.LEFT));
  }

  @Test
  void barcodeRejectsMissingAlignment() {
    assertThrows(IllegalArgumentException.class, () -> new Barcode("12345678", "code128", null));
  }

  @Test
  void barcodeDefaultsItsSymbology() {
    assertEquals(Barcode.DEFAULT_SYMBOLOGY, new Barcode("12345678", null, Alignment.LEFT).symbology());
    assertEquals(Barcode.DEFAULT_SYMBOLOGY, new Barcode("12345678", "", Alignment.LEFT).symbology());
    assertEquals("upc-a", new Barcode("12345678", "upc-a", Alignment.LEFT).symbology());
  }

  @Test
  void feedRejectsNonPositiveLines() {
    assertThrows(IllegalArgumentException.class, () -> new Feed(0));
    assertThrows(IllegalArgumentException.class, () -> new Feed(-1));
    assertEquals(3, new Feed(3).lines());
  }

  @Test
  void cutCarriesItsMode() {
    assertTrue(new Cut(true).partial());
    assertFalse(new Cut(false).partial());
  }

  @Test
  void cutDefaultsToOnePointAndAcceptsThree() {
    assertEquals(1, new Cut(true).points()); // convenience constructor defaults to one point
    assertEquals(1, new Cut(true, 1).points());
    assertEquals(3, new Cut(true, 3).points());
  }

  @Test
  void cutRejectsPointCountsOtherThanOneOrThree() {
    assertThrows(IllegalArgumentException.class, () -> new Cut(true, 0));
    assertThrows(IllegalArgumentException.class, () -> new Cut(true, 2));
    assertThrows(IllegalArgumentException.class, () -> new Cut(true, 4));
  }

  @Test
  void drawerRejectsPinsOtherThanTwoOrFive() {
    assertThrows(IllegalArgumentException.class, () -> new Drawer(0));
    assertThrows(IllegalArgumentException.class, () -> new Drawer(3));
    assertEquals(2, new Drawer().pin());
    assertEquals(2, new Drawer(2).pin());
    assertEquals(5, new Drawer(5).pin());
  }

  @Test
  void placementRejectsNegativePositionAndMissingContent() {
    Paragraph content = new Paragraph(List.of(RUN), Alignment.LEFT);
    assertThrows(IllegalArgumentException.class, () -> new Placement(-1, 0, content));
    assertThrows(IllegalArgumentException.class, () -> new Placement(0, -1, content));
    assertThrows(IllegalArgumentException.class, () -> new Placement(0, 0, null));
  }

  @Test
  void canvasRejectsNonPositiveSizeAndNulls() {
    assertThrows(IllegalArgumentException.class, () -> new Canvas(0, 100, Canvas.Direction.NORMAL, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Canvas(100, 0, Canvas.Direction.NORMAL, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Canvas(100, 100, null, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Canvas(100, 100, Canvas.Direction.NORMAL, null));
  }

  @Test
  void borderRejectsNullStyleAndReportsAnySide() {
    assertThrows(IllegalArgumentException.class, () -> new Border(true, false, false, false, null));
    assertFalse(Border.NONE.any());
    assertTrue(Border.all(Border.Style.SINGLE).any());
  }

  @Test
  void tableRejectsNullOuterAndDefaultsToBorderless() {
    final List<List<Cell>> rows = List.of(List.of(new Cell(List.of(RUN), Alignment.LEFT)));
    assertThrows(IllegalArgumentException.class, () -> new Table(rows, null, false));

    final Table plain = new Table(rows);
    assertEquals(Border.NONE, plain.outer());
    assertFalse(plain.bordered());
  }

  @Test
  void canvasBuilderCollectsPlacements() {
    Paragraph a = new Paragraph(List.of(RUN), Alignment.LEFT);
    Barcode b = new Barcode("12345678", "code128", Alignment.LEFT);
    Canvas canvas = Canvas.of(512, 160).place(10, 20, a).place(30, 40, b).build();

    assertEquals(512, canvas.widthDots());
    assertEquals(160, canvas.heightDots());
    assertEquals(Canvas.Direction.NORMAL, canvas.direction());
    assertEquals(List.of(new Placement(10, 20, a), new Placement(30, 40, b)), canvas.placements());
  }
}
