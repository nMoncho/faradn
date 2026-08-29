package io.nmoncho.faradn.printer.escpos;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.nmoncho.faradn.UnsupportedBlockException;
import io.nmoncho.faradn.document.Barcode;
import io.nmoncho.faradn.document.Block;
import io.nmoncho.faradn.document.Cell;
import io.nmoncho.faradn.document.ComputedStyle;
import io.nmoncho.faradn.document.ComputedStyle.Alignment;
import io.nmoncho.faradn.document.Cut;
import io.nmoncho.faradn.document.Feed;
import io.nmoncho.faradn.document.ImageBlock;
import io.nmoncho.faradn.document.Paragraph;
import io.nmoncho.faradn.document.Rule;
import io.nmoncho.faradn.document.Table;
import io.nmoncho.faradn.document.TextRun;
import io.nmoncho.faradn.printer.PrinterProfile;
import io.nmoncho.faradn.printer.escpos.commands.BarcodeCommands;
import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands;
import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands.CharacterSize;
import io.nmoncho.faradn.printer.escpos.commands.CharacterCommands.Lines;
import io.nmoncho.faradn.printer.escpos.commands.MechanismControlCommands;
import io.nmoncho.faradn.printer.escpos.commands.MiscellaneousCommands;
import io.nmoncho.faradn.printer.escpos.commands.PrintCommands;
import io.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands;
import io.nmoncho.faradn.printer.escpos.commands.PrintPositionCommands.Justification;

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
      if (block instanceof Paragraph paragraph) {
        current = renderParagraph(out, enc, current, paragraph);
      } else if (block instanceof Rule) {
        current = renderRule(out, enc, current);
      } else if (block instanceof Feed feed) {
        out.writeBytes(PrintCommands.PRINT_AND_FEED_LINES.getCode(Lines.of(feed.lines())));
      } else if (block instanceof Cut cut) {
        out.writeBytes(cutCommand(cut.partial()));
      } else if (block instanceof ImageBlock image) {
        current = renderImage(out, current, image);
      } else if (block instanceof Barcode barcode) {
        current = renderBarcode(out, current, barcode);
      } else if (block instanceof Table table) {
        current = renderTable(out, enc, current, table);
      } else {
        throw new UnsupportedBlockException(block);
      }
    }

    // Avoid double cutting if job already has a cut
    final boolean endsWithCut = !blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof Cut;
    endOfJob(out, endsWithCut);

    return out.toByteArray();
  }

  private ComputedStyle renderParagraph(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Paragraph paragraph) {
    current = applyAlignment(out, current, paragraph.alignment());

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
    return current;
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

  private ComputedStyle renderTable(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Table table) {
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, Alignment.LEFT);

    final int columnCount = columnCount(table);
    if (columnCount == 0) {
      return current;
    }
    final int[] widths = columnWidths(table, columnCount, tableColumns(table));

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
  private int[] columnWidths(Table table, int columnCount, int columns) {
    final int available = Math.max(columnCount, columns - (columnCount - 1) * TABLE_COLUMN_GUTTER);
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
