package net.nmoncho.faradn.printer.escpos;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.nmoncho.faradn.UnsupportedBlockException;
import net.nmoncho.faradn.document.Barcode;
import net.nmoncho.faradn.document.Block;
import net.nmoncho.faradn.document.Border;
import net.nmoncho.faradn.document.Box;
import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.Cell;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Cut;
import net.nmoncho.faradn.document.Feed;
import net.nmoncho.faradn.document.ImageBlock;
import net.nmoncho.faradn.document.LeaderLine;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.Placeable;
import net.nmoncho.faradn.document.Placement;
import net.nmoncho.faradn.document.Rule;
import net.nmoncho.faradn.document.Table;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.escpos.commands.BarcodeCommands;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.CharacterSize;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.Lines;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit2D;
import net.nmoncho.faradn.printer.escpos.commands.LineSpacingCommands;
import net.nmoncho.faradn.printer.escpos.commands.MechanismControlCommands;
import net.nmoncho.faradn.printer.escpos.commands.MiscellaneousCommands;
import net.nmoncho.faradn.printer.escpos.commands.PrintCommands;
import net.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands;
import net.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands.Justification;
import net.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands.PrintArea;
import net.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands.Word16;

/**
 * Renders the intermediate representation ({@code List<Block>}) into ESC/POS
 * bytes for a given {@link PrinterProfile}.
 * <p>
 * The renderer is a pure, deterministic function of its input: it performs no
 * I/O and holds no mutable state between calls, which is what makes it
 * golden-byte testable. It tracks the style currently applied on the printer
 * and
 * emits only the commands for what actually changes between consecutive runs,
 * so
 * the output stays close to minimal.
 * <p>
 * Every job is framed by {@code ESC @} (initialize) and an {@code ESC t} code
 * page selection at the start, and an end-of-job feed (and cut, if the profile
 * supports one) at the end. Text is encoded through a {@link CodePageEncoder},
 * which switches code pages inline for glyphs outside the current one instead
 * of
 * dropping them to {@code '?'}. Paragraphs are word-wrapped to the profile's
 * column budget; images rasterize to {@code GS v 0}; barcodes go through
 * {@link BarcodeCommands}; tables are laid out on a character grid.
 */
public final class EscPosRenderer {

  private static final Logger log = LoggerFactory.getLogger(EscPosRenderer.class);

  private static final String RULE_CHARACTER = "-";
  private static final int END_OF_JOB_FEED_LINES = 4;
  private static final int TABLE_COLUMN_GUTTER = 1;

  private final PrinterProfile profile;

  public EscPosRenderer(PrinterProfile profile) {
    if (profile == null) {
      throw new IllegalArgumentException("profile must not be null");
    }
    this.profile = profile;
  }

  /**
   * Renders a block sequence into a complete ESC/POS print job.
   *
   * @param blocks
   *        the intermediate representation, in reading order
   * @return the ESC/POS byte stream to send to the printer
   */
  public byte[] render(List<Block> blocks) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    out.writeBytes(MiscellaneousCommands.INITIALIZE.getCode());
    out.writeBytes(new byte[] { Code.ESC, 0x74, (byte) profile.codePage().id() }); // ESC t: select code page

    // Text is encoded through this: it starts on the profile's default page and
    // switches inline (ESC t) among the profile's pages for glyphs outside it.
    final CodePageEncoder enc = new CodePageEncoder(out, profile.codePage(), profile.codePages());

    // ESC @ resets the printer to exactly INITIAL, so that is where the tracked
    // "already applied" style starts.
    ComputedStyle current = ComputedStyle.INITIAL;

    for (Block block : blocks) {
      current = renderBlock(out, enc, current, block);
    }

    // Avoid double cutting if job already has a cut
    final boolean endsWithCut = !blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof Cut;
    endOfJob(out, endsWithCut);

    return out.toByteArray();
  }

  /**
   * Renders one block, dispatching by type; also used to render a {@link Box}'s
   * children.
   */
  private ComputedStyle renderBlock(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Block block) {
    if (block instanceof Paragraph paragraph) {
      return renderParagraph(out, enc, current, paragraph);
    } else if (block instanceof Rule) {
      return renderRule(out, enc, current);
    } else if (block instanceof Feed feed) {
      out.writeBytes(PrintCommands.PRINT_AND_FEED_LINES.getCode(Lines.of(feed.lines())));
      return current;
    } else if (block instanceof Cut cut) {
      out.writeBytes(cutCommand(cut.partial()));
      return current;
    } else if (block instanceof ImageBlock image) {
      return renderImage(out, current, image);
    } else if (block instanceof Barcode barcode) {
      return renderBarcode(out, current, barcode);
    } else if (block instanceof Table table) {
      return renderTable(out, enc, current, table);
    } else if (block instanceof Canvas canvas) {
      return renderCanvas(out, enc, current, canvas);
    } else if (block instanceof Box box) {
      return renderBox(out, enc, current, box);
    } else if (block instanceof LeaderLine leader) {
      return renderLeaderLine(out, enc, current, leader);
    } else {
      throw new UnsupportedBlockException(block);
    }
  }

  /**
   * Renders a {@link LeaderLine}: the left group, then the fill character
   * repeated across the gap, then the right group flush to the edge. If the two
   * groups don't fit on one line, the right group drops to its own right-aligned
   * line.
   */
  private ComputedStyle renderLeaderLine(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      LeaderLine leader) {
    final int columns = leaderColumns(leader);
    final int gap = columns - displayWidth(leader.left()) - displayWidth(leader.right());

    if (gap < 0) {
      current = applyAlignment(out, current, Alignment.LEFT);
      if (!leader.left().isEmpty()) {
        current = emitRuns(out, enc, current, leader.left());
        current = clearInlineStyle(out, current);
        out.writeBytes(PrintCommands.LINE_FEED.getCode());
      }
      current = applyAlignment(out, current, Alignment.RIGHT);
      current = emitRuns(out, enc, current, leader.right());
      current = clearInlineStyle(out, current);
      out.writeBytes(PrintCommands.LINE_FEED.getCode());
      return current;
    }

    current = applyAlignment(out, current, Alignment.LEFT);
    current = emitRuns(out, enc, current, leader.left());
    current = clearInlineStyle(out, current);
    enc.emit(String.valueOf(leader.fill()).repeat(gap));
    current = emitRuns(out, enc, current, leader.right());
    current = clearInlineStyle(out, current);
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * The column budget for a leader line: the narrowest font across both groups.
   */
  private int leaderColumns(LeaderLine leader) {
    final List<TextRun> all = new ArrayList<>(leader.left());
    all.addAll(leader.right());
    return effectiveColumns(all);
  }

  /**
   * The on-paper width of a run list in columns (characters × width multiplier).
   */
  private static int displayWidth(List<TextRun> runs) {
    int width = 0;
    for (TextRun run : runs) {
      width += run.text().length() * run.style().widthMultiple();
    }
    return width;
  }

  /** Emits a run list with each run's inline style applied. */
  private ComputedStyle emitRuns(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      List<TextRun> runs) {
    for (TextRun run : runs) {
      current = applyInlineStyle(out, current, run.style());
      enc.emit(run.text());
    }
    return current;
  }

  private ComputedStyle renderParagraph(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Paragraph paragraph) {
    final Border border = paragraph.border();
    // Side borders frame each line with │ and shrink the content, so they take
    // the box path; top/bottom-only borders stay full-width rules.
    if (border.left() || border.right()) {
      return renderBoxedParagraph(out, enc, current, paragraph, border);
    }

    current = applyAlignment(out, current, paragraph.alignment());
    if (border.top()) {
      current = emitHorizontalBorder(out, enc, current, border.style());
    }

    final OptionalInt spacing = beginLineSpacing(out, paragraph);
    final List<List<TextRun>> lines = TextWrapper.wrap(paragraph.runs(), effectiveColumns(paragraph.runs()));
    for (int i = 0; i < lines.size(); i++) {
      for (TextRun segment : lines.get(i)) {
        current = applyInlineStyle(out, current, segment.style());
        enc.emit(segment.text());
      }
      if (i == lines.size() - 1) {
        // Reset trailing inline state at the end of the paragraph.
        current = clearInlineStyle(out, current);
      }
      out.writeBytes(PrintCommands.LINE_FEED.getCode());
    }
    endLineSpacing(out, spacing);

    if (border.bottom()) {
      current = emitHorizontalBorder(out, enc, current, border.style());
    }
    return current;
  }

  /**
   * Renders a paragraph framed by side borders: a top edge ({@code ┌─┐}), each
   * content line wrapped to {@code columns − sides} between {@code │}s, and a
   * bottom edge ({@code └─┘}). Reuses {@link #emitCell} to pad/align content
   * within the box.
   */
  private ComputedStyle renderBoxedParagraph(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Paragraph paragraph, Border border) {
    final BoxDrawing box = BoxDrawing.of(border.style());
    final int contentWidth = boxContentWidth(border);

    current = applyAlignment(out, current, Alignment.LEFT); // the box itself is full-width
    current = emitBoxEdge(out, enc, current, box, border, contentWidth, true);
    current = emitFramedParagraph(out, enc, current, paragraph, border, box, contentWidth);
    current = emitBoxEdge(out, enc, current, box, border, contentWidth, false);
    return current;
  }

  /**
   * Renders a {@link Box}: a top edge, its children (paragraphs wrapped inside
   * the
   * side rails, other blocks rendered plainly between the edges), then a bottom
   * edge.
   */
  private ComputedStyle renderBox(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current, Box box) {
    final Border border = box.border();
    final BoxDrawing drawing = BoxDrawing.of(border.style());
    final int contentWidth = boxContentWidth(border);

    current = applyAlignment(out, current, Alignment.LEFT);
    current = emitBoxEdge(out, enc, current, drawing, border, contentWidth, true);
    for (Block child : box.children()) {
      if (child instanceof Paragraph paragraph) {
        current = emitFramedParagraph(out, enc, current, paragraph, border, drawing, contentWidth);
      } else {
        current = renderBlock(out, enc, current, child); // non-paragraph child: no side rails (v1)
      }
    }
    current = emitBoxEdge(out, enc, current, drawing, border, contentWidth, false);
    return current;
  }

  /**
   * The content width inside a box: the paper columns minus a cell for each side
   * rail.
   */
  private int boxContentWidth(Border border) {
    return Math.max(1, profile.columns() - (border.left() ? 1 : 0) - (border.right() ? 1 : 0));
  }

  /**
   * Emits a box top ({@code isTop}) or bottom edge, when that side has a border.
   */
  private ComputedStyle emitBoxEdge(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      BoxDrawing box, Border border, int contentWidth, boolean isTop) {
    if (isTop ? !border.top() : !border.bottom()) {
      return current;
    }
    current = clearInlineStyle(out, current);
    enc.emit(horizontalEdge(box, border, contentWidth, isTop));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * Emits a paragraph's wrapped lines framed by the box's side rails ({@code │}),
   * padded to {@code contentWidth}.
   */
  private ComputedStyle emitFramedParagraph(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Paragraph paragraph, Border border, BoxDrawing box, int contentWidth) {
    final OptionalInt spacing = beginLineSpacing(out, paragraph);
    for (List<TextRun> line : TextWrapper.wrap(paragraph.runs(), contentWidth)) {
      current = clearInlineStyle(out, current);
      if (border.left()) {
        enc.emit(box.vertical());
      }
      current = emitCell(out, enc, current, line, contentWidth, paragraph.alignment());
      if (border.right()) {
        current = clearInlineStyle(out, current);
        enc.emit(box.vertical());
      }
      out.writeBytes(PrintCommands.LINE_FEED.getCode());
    }
    endLineSpacing(out, spacing);
    return current;
  }

  /**
   * A box top ({@code true}) or bottom edge: a corner where a side border exists,
   * a plain {@code ─} where it does not, around {@code ─×contentWidth}.
   */
  private static String horizontalEdge(BoxDrawing box, Border border, int contentWidth, boolean top) {
    final StringBuilder edge = new StringBuilder();
    if (border.left()) {
      edge.append(top ? box.topLeft() : box.bottomLeft());
    }
    edge.append(box.horizontal().repeat(contentWidth));
    if (border.right()) {
      edge.append(top ? box.topRight() : box.bottomRight());
    }
    return edge.toString();
  }

  /**
   * Emits a full-width horizontal rule ({@code ─}/{@code ═}) for a paragraph's
   * top/bottom border.
   */
  private ComputedStyle emitHorizontalBorder(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Border.Style style) {
    current = clearInlineStyle(out, current);
    enc.emit(BoxDrawing.of(style).horizontal().repeat(profile.columns()));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * Pins the line spacing for a paragraph's {@code line-height} (via GS P + ESC 3
   * n) and returns the spacing so {@link #endLineSpacing} can restore the
   * default;
   * empty when the paragraph uses the default spacing.
   */
  private OptionalInt beginLineSpacing(ByteArrayOutputStream out, Paragraph paragraph) {
    final OptionalInt spacing = paragraph.runs().get(0).style().lineHeight()
        .resolveDots(textCellHeightDots(paragraph), profile.dpi());
    if (spacing.isPresent()) {
      final int dpi = profile.dpi();
      if (dpi >= 1 && dpi <= 255) {
        out.writeBytes(PrintPositionCommands.SET_MOTION_UNITS.getCode(new MotionUnit2D(dpi, dpi))); // GS P
      }
      out.writeBytes(LineSpacingCommands.SET_LINE_SPACING.getCode(new MotionUnit(spacing.getAsInt()))); // ESC 3 n
    }
    return spacing;
  }

  private void endLineSpacing(ByteArrayOutputStream out, OptionalInt spacing) {
    if (spacing.isPresent()) {
      out.writeBytes(LineSpacingCommands.DEFAULT_LINE_SPACING.getCode()); // ESC 2
    }
  }

  private ComputedStyle renderRule(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current) {
    current = clearInlineStyle(out, current);
    enc.emit(RULE_CHARACTER.repeat(profile.columns()));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  private ComputedStyle renderImage(ByteArrayOutputStream out, ComputedStyle current, ImageBlock image) {
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, image.alignment());
    out.writeBytes(ImageRasterizer.raster(image.image().raster(), profile.dotsPerLine()));
    return current;
  }

  private ComputedStyle renderBarcode(ByteArrayOutputStream out, ComputedStyle current, Barcode barcode) {
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, barcode.alignment());
    out.writeBytes(BarcodeCommands.encode(barcode.symbology(), barcode.data(), barcode.options()));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * Renders a {@link Canvas} via ESC/POS page mode: enter page mode, set the
   * print area and direction, place each child at its absolute dot position,
   * then print the buffered page and return to standard mode ({@code FF}).
   */
  private ComputedStyle renderCanvas(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Canvas canvas) {
    if (!profile.supportsPageMode()) {
      // Best effort: still emit the page-mode sequence, but warn - the region may
      // print blank or garbled on a model that does not support it.
      log.warn("Profile [{}] does not support page mode; the positioned region may not print correctly",
          profile.name());
    }
    current = clearInlineStyle(out, current);

    // Pin the motion unit to the profile's dpi so positions in dots map 1:1.
    // GS P takes a single-byte denominator, so this only applies for dpi <= 255
    // (true for ESC/POS receipt printers, e.g. the TM-T88V at 180).
    final int dpi = profile.dpi();
    if (dpi >= 1 && dpi <= 255) {
      out.writeBytes(PrintPositionCommands.SET_MOTION_UNITS.getCode(new MotionUnit2D(dpi, dpi))); // GS P
    }
    out.writeBytes(PrintCommands.SELECT_PAGE_MODE.getCode()); // ESC L (must be at a line start)
    out.writeBytes(PrintPositionCommands.SET_PRINT_AREA
        .getCode(new PrintArea(0, 0, canvas.widthDots(), canvas.heightDots()))); // ESC W
    out.writeBytes(PrintPositionCommands.SELECT_PRINT_DIRECTION.getCode(direction(canvas.direction()))); // ESC T

    for (Placement placement : canvas.placements()) {
      // Placement (x, y) is the top-left of the content, but in page mode GS $
      // anchors baseline-drawn content (text, 1D barcodes) at its *bottom* - the
      // glyphs/bars are drawn upward from there - while rasters (images, 2D codes)
      // develop downward from the position. So drop the vertical position of the
      // former by its height to put its top at y; leave the latter at y.
      final int yDots = placement.yDots() + baselineOffsetDots(placement.content());
      out.writeBytes(PrintPositionCommands.SET_ABSOLUTE_PRINT_POSITION.getCode(new Word16(placement.xDots()))); // ESC $
      out.writeBytes(PrintPositionCommands.SET_ABSOLUTE_VERTICAL_PRINT_POSITION.getCode(new Word16(yDots))); // GS $
      current = renderPlacement(out, enc, current, placement, canvas.widthDots());
    }

    current = clearInlineStyle(out, current);
    out.writeBytes(PrintCommands.PRINT_AND_GOTO_STANDARD.getCode()); // FF: print the page + return to standard mode
    return current;
  }

  /**
   * Renders one positioned child. The cursor is already at the placement's
   * {@code (x, y)}; text flows and wraps within the print area, so no word-wrap
   * or block alignment is applied here.
   */
  private ComputedStyle renderPlacement(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Placement placement, int areaWidthDots) {
    current = clearInlineStyle(out, current); // each placement starts from a clean style
    final Placeable content = placement.content();
    if (content instanceof Paragraph paragraph) {
      for (TextRun segment : paragraph.runs()) {
        current = applyInlineStyle(out, current, segment.style());
        enc.emit(segment.text());
      }
    } else if (content instanceof ImageBlock image) {
      out.writeBytes(ImageRasterizer.raster(image.image().raster(), Math.max(1, areaWidthDots - placement.xDots())));
    } else if (content instanceof Barcode barcode) {
      out.writeBytes(BarcodeCommands.encode(barcode.symbology(), barcode.data(), barcode.options()));
    }
    return current;
  }

  /**
   * The height in dots of the tallest character cell in a paragraph, used to
   * drop text's page-mode baseline so the placement's {@code y} lands at the top
   * of the text. ESC/POS has no command to query glyph height, so it is derived
   * from the font's on-paper character width (cells are ~2:1 - Font&nbsp;A is
   * 12&times;24, Font&nbsp;B 9&times;17) and scaled by the run's height
   * multiplier.
   */
  private int textCellHeightDots(Paragraph paragraph) {
    int max = 0;
    for (TextRun run : paragraph.runs()) {
      int columns = profile.font(run.style().font()).columns();
      int charWidthDots = Math.max(1, Math.round((float) profile.dotsPerLine() / columns));
      max = Math.max(max, charWidthDots * 2 * run.style().heightMultiple());
    }
    return max;
  }

  /**
   * How far to drop a placement's page-mode vertical position so its {@code y}
   * lands at the top. Baseline-anchored content (text; 1D barcodes, whose bars
   * are drawn upward from the {@code GS $} position) offsets by its height;
   * rasters (images, 2D codes) develop downward from the position, so they do
   * not.
   */
  private int baselineOffsetDots(Placeable content) {
    if (content instanceof Paragraph paragraph) {
      return textCellHeightDots(paragraph);
    }
    if (content instanceof Barcode barcode && !BarcodeCommands.isTwoDimensional(barcode.symbology())) {
      return barcode.options().heightDots();
    }
    return 0;
  }

  /** Maps a canvas direction to the {@code ESC T} print direction. */
  private static PrintPositionCommands.Direction direction(Canvas.Direction direction) {
    return switch (direction) {
      case NORMAL -> PrintPositionCommands.Direction.LEFT_TO_RIGHT;
      case ROTATE_90_CW -> PrintPositionCommands.Direction.TOP_TO_BOTTOM;
      case ROTATE_180 -> PrintPositionCommands.Direction.RIGHT_TO_LEFT;
      case ROTATE_90_CCW -> PrintPositionCommands.Direction.BOTTOM_TO_TOP;
    };
  }

  private ComputedStyle renderTable(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Table table) {
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, Alignment.LEFT);

    final int columnCount = columnCount(table);
    if (columnCount == 0) {
      return current;
    }
    // Bordered tables spend columnCount+1 cells on the vertical rules (a │ at each
    // edge and between columns); borderless ones spend columnCount-1 space gutters.
    final int separatorTotal = table.bordered() ? (columnCount + 1) : (columnCount - 1) * TABLE_COLUMN_GUTTER;
    final int available = Math.max(columnCount, tableColumns(table) - separatorTotal);
    final int[] widths = columnWidths(table, columnCount, available);

    if (table.bordered()) {
      return renderBorderedTable(out, enc, current, table, widths, columnCount);
    }

    for (List<Cell> row : table.rows()) {
      final List<PlacedCell> placed = placeRow(row, widths, columnCount);

      int rowHeight = 1;
      for (PlacedCell cell : placed) {
        rowHeight = Math.max(rowHeight, cell.lines().size());
      }

      for (int line = 0; line < rowHeight; line++) {
        for (int i = 0; i < placed.size(); i++) {
          final PlacedCell cell = placed.get(i);
          final List<TextRun> segments = line < cell.lines().size() ? cell.lines().get(line) : List.of();
          current = emitCell(out, enc, current, segments, cell.width(), cell.alignment());
          if (i < placed.size() - 1) {
            current = clearInlineStyle(out, current);
            enc.emit(" ".repeat(TABLE_COLUMN_GUTTER));
          }
        }
        current = clearInlineStyle(out, current);
        out.writeBytes(PrintCommands.LINE_FEED.getCode());
      }
    }
    return current;
  }

  /**
   * Renders a table with box-drawing borders: a top frame, each row's content
   * lines wrapped in vertical rules ({@code │}), a separator between rows, and a
   * bottom frame. The grid is uniform - a {@code colspan}'s interior joins are
   * drawn as if the cell were split (a v1 simplification).
   */
  private ComputedStyle renderBorderedTable(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Table table, int[] widths, int columnCount) {
    final BoxDrawing box = BoxDrawing.of(table.outer().style());
    final List<List<Cell>> rows = table.rows();

    // Per row, which interior column boundaries carry a vertical divider (a
    // colspan cell has none across the columns it merges). A rule's join glyph is
    // chosen from whether a divider meets it from the row above and/or below.
    final boolean[] noDivider = new boolean[columnCount];
    final boolean[][] dividers = new boolean[rows.size()][];
    for (int r = 0; r < rows.size(); r++) {
      dividers[r] = dividerBoundaries(rows.get(r), columnCount);
    }

    current = clearInlineStyle(out, current);
    enc.emit(horizontalRule(box, widths, box.topLeft(), box.topRight(), noDivider, dividers[0]));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());

    for (int r = 0; r < rows.size(); r++) {
      final List<PlacedCell> placed = placeRow(rows.get(r), widths, columnCount);
      int rowHeight = 1;
      for (PlacedCell cell : placed) {
        rowHeight = Math.max(rowHeight, cell.lines().size());
      }

      for (int line = 0; line < rowHeight; line++) {
        current = clearInlineStyle(out, current);
        enc.emit(box.vertical()); // left edge
        for (PlacedCell cell : placed) {
          final List<TextRun> segments = line < cell.lines().size() ? cell.lines().get(line) : List.of();
          current = emitCell(out, enc, current, segments, cell.width(), cell.alignment());
          current = clearInlineStyle(out, current);
          enc.emit(box.vertical()); // column separator / right edge
        }
        out.writeBytes(PrintCommands.LINE_FEED.getCode());
      }

      if (r < rows.size() - 1) {
        current = clearInlineStyle(out, current);
        enc.emit(horizontalRule(box, widths, box.teeRight(), box.teeLeft(), dividers[r], dividers[r + 1]));
        out.writeBytes(PrintCommands.LINE_FEED.getCode());
      }
    }

    current = clearInlineStyle(out, current);
    enc.emit(horizontalRule(box, widths, box.bottomLeft(), box.bottomRight(), dividers[rows.size() - 1], noDivider));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * Which interior column boundaries of a row carry a vertical divider. Index
   * {@code b} (for {@code 1 ≤ b < columnCount}) is the boundary between columns
   * {@code b-1} and {@code b}; it has a divider unless a cell spans across it.
   */
  private static boolean[] dividerBoundaries(List<Cell> row, int columnCount) {
    final boolean[] divider = new boolean[columnCount];
    int col = 0;
    for (Cell cell : row) {
      if (col >= columnCount) {
        break;
      }
      if (col > 0) {
        divider[col] = true; // this cell's left edge is a divider
      }
      col += Math.min(cell.colSpan(), columnCount - col);
    }
    while (col < columnCount) { // columns the row leaves uncovered are single cells
      if (col > 0) {
        divider[col] = true;
      }
      col++;
    }
    return divider;
  }

  /**
   * A full-width horizontal rule: {@code left}, each column's {@code ─×width},
   * {@code right}, with each interior join chosen from whether a vertical divider
   * meets it from the rule's upper row ({@code above}) and lower row
   * ({@code below}).
   */
  private static String horizontalRule(BoxDrawing box, int[] widths, String left, String right,
      boolean[] above, boolean[] below) {
    final StringBuilder rule = new StringBuilder(left);
    for (int c = 0; c < widths.length; c++) {
      rule.append(box.horizontal().repeat(widths[c]));
      rule.append(c < widths.length - 1 ? joinGlyph(box, above[c + 1], below[c + 1]) : right);
    }
    return rule.toString();
  }

  /**
   * The rule glyph at a boundary given whether a divider meets it from above /
   * below.
   */
  private static String joinGlyph(BoxDrawing box, boolean above, boolean below) {
    if (above && below) {
      return box.cross();
    }
    if (above) {
      return box.teeUp();
    }
    if (below) {
      return box.teeDown();
    }
    return box.horizontal();
  }

  /**
   * A cell placed on the grid: its wrapped lines, the width it occupies, and its
   * alignment.
   */
  private record PlacedCell(List<List<TextRun>> lines, int width, Alignment alignment) {
  }

  /**
   * Places a row's cells across the grid, wrapping each to its (spanned) width;
   * any columns the row leaves uncovered become empty cells so every line is the
   * same width.
   */
  private List<PlacedCell> placeRow(List<Cell> row, int[] widths, int columnCount) {
    final List<PlacedCell> placed = new ArrayList<>();
    int col = 0;
    for (Cell cell : row) {
      if (col >= columnCount) {
        break;
      }
      final int span = Math.min(cell.colSpan(), columnCount - col);
      final int width = spannedWidth(widths, col, span);
      final List<List<TextRun>> lines = cell.content().isEmpty()
          ? List.of()
          : TextWrapper.wrap(cell.content(), width);
      placed.add(new PlacedCell(lines, width, cell.alignment()));
      col += span;
    }
    while (col < columnCount) {
      placed.add(new PlacedCell(List.of(), widths[col], Alignment.LEFT));
      col++;
    }
    return placed;
  }

  private int columnCount(Table table) {
    int max = 0;
    for (List<Cell> row : table.rows()) {
      int span = 0;
      for (Cell cell : row) {
        span += cell.colSpan();
      }
      max = Math.max(max, span);
    }
    return max;
  }

  /**
   * The column budget for a run of text: the smallest column count among the
   * fonts its runs use. A uniform-font run gets that font's full budget; a mixed
   * one gets the narrowest-glyph (fewest-columns) font's budget, so it never
   * overflows.
   */
  private int effectiveColumns(List<TextRun> runs) {
    if (runs.isEmpty()) {
      return profile.columns();
    }
    int columns = Integer.MAX_VALUE;
    for (TextRun run : runs) {
      columns = Math.min(columns, profile.font(run.style().font()).columns());
    }
    return columns;
  }

  /**
   * A table's column budget is the smallest column count among the fonts its
   * cells use.
   */
  private int tableColumns(Table table) {
    int columns = Integer.MAX_VALUE;
    for (List<Cell> row : table.rows()) {
      for (Cell cell : row) {
        for (TextRun run : cell.content()) {
          columns = Math.min(columns, profile.font(run.style().font()).columns());
        }
      }
    }
    return columns == Integer.MAX_VALUE ? profile.columns() : columns;
  }

  /**
   * Column widths sized to content: each column takes the widest content among
   * the
   * single-column cells in it, then the leftover is handed out proportionally to
   * fill the line (or content is shrunk proportionally when it overflows the
   * budget).
   */
  private int[] columnWidths(Table table, int columnCount, int available) {
    final int[] natural = new int[columnCount];
    for (List<Cell> row : table.rows()) {
      int col = 0;
      for (Cell cell : row) {
        if (col >= columnCount) {
          break;
        }
        final int span = Math.min(cell.colSpan(), columnCount - col);
        if (span == 1) {
          natural[col] = Math.max(natural[col], contentWidth(cell));
        }
        col += span;
      }
    }
    return fit(natural, available);
  }

  private static int[] fit(int[] natural, int available) {
    final int columnCount = natural.length;
    int sum = 0;
    for (int width : natural) {
      sum += width;
    }
    final int[] widths = new int[columnCount];
    if (sum == 0) {
      // No content to measure: fall back to an even split.
      Arrays.fill(widths, available / columnCount);
      for (int c = 0; c < available % columnCount; c++) {
        widths[c]++;
      }
      return ensureMin(widths);
    }
    if (sum <= available) {
      final int leftover = available - sum;
      for (int c = 0; c < columnCount; c++) {
        widths[c] = natural[c] + (int) ((long) leftover * natural[c] / sum);
      }
    } else {
      for (int c = 0; c < columnCount; c++) {
        widths[c] = (int) ((long) natural[c] * available / sum);
      }
    }
    distributeRemainder(widths, natural, available);
    return ensureMin(widths);
  }

  /**
   * Hands any rounding remainder to the widest column, keeping the total at
   * {@code available}.
   */
  private static void distributeRemainder(int[] widths, int[] natural, int available) {
    int used = 0;
    for (int width : widths) {
      used += width;
    }
    int widest = 0;
    for (int c = 1; c < natural.length; c++) {
      if (natural[c] > natural[widest]) {
        widest = c;
      }
    }
    widths[widest] += available - used;
  }

  private static int[] ensureMin(int[] widths) {
    for (int c = 0; c < widths.length; c++) {
      if (widths[c] < 1) {
        widths[c] = 1;
      }
    }
    return widths;
  }

  private static int spannedWidth(int[] widths, int col, int span) {
    int width = (span - 1) * TABLE_COLUMN_GUTTER;
    for (int c = col; c < col + span; c++) {
      width += widths[c];
    }
    return width;
  }

  private static int contentWidth(Cell cell) {
    int width = 0;
    for (TextRun run : cell.content()) {
      width += run.text().length() * run.style().widthMultiple();
    }
    return width;
  }

  private ComputedStyle emitCell(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      List<TextRun> segments, int columnWidth, Alignment alignment) {
    int textWidth = 0;
    for (TextRun segment : segments) {
      textWidth += segment.text().length() * segment.style().widthMultiple();
    }
    final int pad = Math.max(0, columnWidth - textWidth);
    final int leftPad = switch (alignment) {
      case RIGHT -> pad;
      case CENTER -> pad / 2;
      default -> 0;
    };
    final int rightPad = pad - leftPad;

    if (leftPad > 0) {
      current = clearInlineStyle(out, current);
      enc.emit(" ".repeat(leftPad));
    }
    for (TextRun segment : segments) {
      current = applyInlineStyle(out, current, segment.style());
      enc.emit(segment.text());
    }
    if (rightPad > 0) {
      current = clearInlineStyle(out, current);
      enc.emit(" ".repeat(rightPad));
    }
    return current;
  }

  /**
   * Alignment is a whole-line property ({@code ESC a}), emitted once per block.
   */
  private ComputedStyle applyAlignment(ByteArrayOutputStream out, ComputedStyle current, Alignment alignment) {
    if (current.alignment() == alignment) {
      return current;
    }
    out.writeBytes(PrintPositionCommands.SELECT_JUSTIFICATION.getCode(justification(alignment)));
    return withAlignment(current, alignment);
  }

  /**
   * Emits the commands to move from {@code current} to {@code target} for the
   * per-run attributes (bold, underline, size, invert), leaving alignment
   * untouched. Only the attributes that differ produce output.
   */
  private ComputedStyle applyInlineStyle(ByteArrayOutputStream out, ComputedStyle current, ComputedStyle target) {
    if (current.bold() != target.bold()) {
      out.writeBytes(target.bold() ? CharacterCommands.EMPHASIZED.turnOn() : CharacterCommands.EMPHASIZED.turnOff());
    }
    if (current.underline() != target.underline()) {
      out.writeBytes(target.underline() ? CharacterCommands.UNDERLINE.turnOn() : CharacterCommands.UNDERLINE.turnOff());
    }
    if (current.widthMultiple() != target.widthMultiple() || current.heightMultiple() != target.heightMultiple()) {
      out.writeBytes(CharacterCommands.SELECT_CHARACTER_SIZE
          .getCode(new CharacterSize(target.widthMultiple(), target.heightMultiple())));
    }
    if (current.invert() != target.invert()) {
      out.writeBytes(target.invert()
          ? CharacterCommands.REVERSE_BACKGROUND.turnOn()
          : CharacterCommands.REVERSE_BACKGROUND.turnOff());
    }
    if (current.font() != target.font()) {
      out.writeBytes(new byte[] { Code.ESC, 0x4D, (byte) target.font() }); // ESC M: select font slot
    }
    if (current.italic() != target.italic()) {
      out.writeBytes(target.italic()
          ? CharacterCommands.SELECT_ITALIC.getCode()
          : CharacterCommands.CANCEL_ITALIC.getCode());
    }
    return new ComputedStyle(target.bold(), target.underline(), target.widthMultiple(), target.heightMultiple(),
        current.alignment(), target.invert(), target.font(), target.italic());
  }

  /** Turns off every per-run attribute, emitting only what is currently on. */
  private ComputedStyle clearInlineStyle(ByteArrayOutputStream out, ComputedStyle current) {
    final ComputedStyle cleared = new ComputedStyle(false, false, 1, 1, current.alignment(), false);
    return applyInlineStyle(out, current, cleared);
  }

  private void endOfJob(ByteArrayOutputStream out, boolean alreadyCut) {
    // A document that ends with an explicit Cut has already framed its end.
    if (alreadyCut) {
      return;
    }
    out.writeBytes(PrintCommands.PRINT_AND_FEED_LINES.getCode(Lines.of(END_OF_JOB_FEED_LINES)));
    if (profile.supportsCut()) {
      out.writeBytes(cutCommand(true));
    }
  }

  private static byte[] cutCommand(boolean partial) {
    return (partial ? MechanismControlCommands.PARTIAL_CUT : MechanismControlCommands.FULL_CUT).getCode();
  }

  private static ComputedStyle withAlignment(ComputedStyle style, Alignment alignment) {
    return new ComputedStyle(style.bold(), style.underline(), style.widthMultiple(), style.heightMultiple(),
        alignment, style.invert(), style.font(), style.italic());
  }

  private static Justification justification(Alignment alignment) {
    return switch (alignment) {
      case LEFT -> Justification.LEFT;
      case CENTER -> Justification.CENTER;
      case RIGHT -> Justification.RIGHT;
    };
  }

}
