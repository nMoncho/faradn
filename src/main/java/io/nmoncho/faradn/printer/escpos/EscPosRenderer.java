package io.nmoncho.faradn.printer.escpos;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
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
 * supports one) at the end. Paragraphs are word-wrapped to the profile's column
 * budget; images rasterize to {@code GS v 0}; barcodes go through
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

    // ESC @ resets the printer to exactly INITIAL, so that is where the tracked
    // "already applied" style starts.
    ComputedStyle current = ComputedStyle.INITIAL;

    for (Block block : blocks) {
      if (block instanceof Paragraph paragraph) {
        current = renderParagraph(out, current, paragraph);
      } else if (block instanceof Rule) {
        current = renderRule(out, current);
      } else if (block instanceof Feed feed) {
        out.writeBytes(PrintCommands.PRINT_AND_FEED_LINES.getCode(Lines.of(feed.lines())));
      } else if (block instanceof Cut cut) {
        out.writeBytes(cutCommand(cut.partial()));
      } else if (block instanceof ImageBlock image) {
        current = renderImage(out, current, image);
      } else if (block instanceof Barcode barcode) {
        current = renderBarcode(out, current, barcode);
      } else if (block instanceof Table table) {
        current = renderTable(out, current, table);
      } else {
        throw new UnsupportedBlockException(block);
      }
    }

    endOfJob(out);
    return out.toByteArray();
  }

  private ComputedStyle renderParagraph(ByteArrayOutputStream out, ComputedStyle current, Paragraph paragraph) {
    current = applyAlignment(out, current, paragraph.alignment());

    final List<List<TextRun>> lines = TextWrapper.wrap(paragraph.runs(), profile.columns());
    for (int i = 0; i < lines.size(); i++) {
      for (TextRun segment : lines.get(i)) {
        current = applyInlineStyle(out, current, segment.style());
        out.writeBytes(encode(segment.text()));
      }
      if (i == lines.size() - 1) {
        // Reset trailing inline state at the end of the paragraph.
        current = clearInlineStyle(out, current);
      }
      out.writeBytes(PrintCommands.LINE_FEED.getCode());
    }
    return current;
  }

  private ComputedStyle renderRule(ByteArrayOutputStream out, ComputedStyle current) {
    current = clearInlineStyle(out, current);
    out.writeBytes(encode(RULE_CHARACTER.repeat(profile.columns())));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  private ComputedStyle renderImage(ByteArrayOutputStream out, ComputedStyle current, ImageBlock image) {
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, image.alignment());
    out.writeBytes(ImageRasterizer.raster(image.image().load(), profile.dotsPerLine()));
    return current;
  }

  private ComputedStyle renderBarcode(ByteArrayOutputStream out, ComputedStyle current, Barcode barcode) {
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, barcode.alignment());
    out.writeBytes(BarcodeCommands.encode(barcode.symbology(), barcode.data()));
    out.writeBytes(PrintCommands.LINE_FEED.getCode());
    return current;
  }

  private ComputedStyle renderTable(ByteArrayOutputStream out, ComputedStyle current, Table table) {
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, Alignment.LEFT);

    final int columnCount = table.rows().stream().mapToInt(List::size).max().orElse(0);
    if (columnCount == 0) {
      return current;
    }
    final int columnWidth = Math.max(1,
        (profile.columns() - (columnCount - 1) * TABLE_COLUMN_GUTTER) / columnCount);

    for (List<Cell> row : table.rows()) {
      final List<List<List<TextRun>>> wrapped = new ArrayList<>();
      for (int c = 0; c < columnCount; c++) {
        final List<TextRun> content = c < row.size() ? row.get(c).content() : List.of();
        wrapped.add(content.isEmpty() ? List.of() : TextWrapper.wrap(content, columnWidth));
      }
      int rowHeight = 1;
      for (List<List<TextRun>> cellLines : wrapped) {
        rowHeight = Math.max(rowHeight, cellLines.size());
      }

      for (int line = 0; line < rowHeight; line++) {
        for (int c = 0; c < columnCount; c++) {
          final List<List<TextRun>> cellLines = wrapped.get(c);
          final List<TextRun> segments = line < cellLines.size() ? cellLines.get(line) : List.of();
          final Alignment alignment = c < row.size() ? row.get(c).alignment() : Alignment.LEFT;
          current = emitCell(out, current, segments, columnWidth, alignment);
          if (c < columnCount - 1) {
            current = clearInlineStyle(out, current);
            out.writeBytes(encode(" ".repeat(TABLE_COLUMN_GUTTER)));
          }
        }
        current = clearInlineStyle(out, current);
        out.writeBytes(PrintCommands.LINE_FEED.getCode());
      }
    }
    return current;
  }

  private ComputedStyle emitCell(ByteArrayOutputStream out, ComputedStyle current, List<TextRun> segments,
      int columnWidth, Alignment alignment) {
    int textWidth = 0;
    for (TextRun segment : segments) {
      textWidth += segment.text().length();
    }
    final int pad = Math.max(0, columnWidth - textWidth);
    final int leftPad = alignment == Alignment.RIGHT ? pad : 0;
    final int rightPad = pad - leftPad;

    if (leftPad > 0) {
      current = clearInlineStyle(out, current);
      out.writeBytes(encode(" ".repeat(leftPad)));
    }
    for (TextRun segment : segments) {
      current = applyInlineStyle(out, current, segment.style());
      out.writeBytes(encode(segment.text()));
    }
    if (rightPad > 0) {
      current = clearInlineStyle(out, current);
      out.writeBytes(encode(" ".repeat(rightPad)));
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
    return new ComputedStyle(target.bold(), target.underline(), target.widthMultiple(), target.heightMultiple(),
        current.alignment(), target.invert());
  }

  /** Turns off every per-run attribute, emitting only what is currently on. */
  private ComputedStyle clearInlineStyle(ByteArrayOutputStream out, ComputedStyle current) {
    final ComputedStyle cleared = new ComputedStyle(false, false, 1, 1, current.alignment(), false);
    return applyInlineStyle(out, current, cleared);
  }

  private void endOfJob(ByteArrayOutputStream out) {
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
        alignment, style.invert());
  }

  private static Justification justification(Alignment alignment) {
    return switch (alignment) {
      case LEFT -> Justification.LEFT;
      case CENTER -> Justification.CENTER;
      case RIGHT -> Justification.RIGHT;
    };
  }

  private byte[] encode(String text) {
    return text.getBytes(profile.codePage().charset());
  }
}
