//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt;

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
import net.nmoncho.faradn.document.BlockLayout;
import net.nmoncho.faradn.document.Border;
import net.nmoncho.faradn.document.Box;
import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.Cell;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Cut;
import net.nmoncho.faradn.document.Drawer;
import net.nmoncho.faradn.document.Feed;
import net.nmoncho.faradn.document.ImageBlock;
import net.nmoncho.faradn.document.LeaderLine;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.Rule;
import net.nmoncho.faradn.document.Space;
import net.nmoncho.faradn.document.Table;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.CodePageEncoder;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.Renderer;
import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.starprnt.commands.StarBarcodeCommands;
import net.nmoncho.faradn.printer.starprnt.commands.StarCharacterCommands;
import net.nmoncho.faradn.printer.starprnt.commands.StarCharacterCommands.FontSlot;
import net.nmoncho.faradn.printer.starprnt.commands.StarCharacterCommands.StarCharacterSize;
import net.nmoncho.faradn.printer.starprnt.commands.StarLineSpacingCommands;
import net.nmoncho.faradn.printer.starprnt.commands.StarLineSpacingCommands.Amount;
import net.nmoncho.faradn.printer.starprnt.commands.StarMechanismCommands;
import net.nmoncho.faradn.printer.starprnt.commands.StarPrintCommands;
import net.nmoncho.faradn.printer.starprnt.commands.StarPrintCommands.Lines;
import net.nmoncho.faradn.printer.starprnt.commands.StarPrintPositionCommands;
import net.nmoncho.faradn.printer.starprnt.commands.StarPrintPositionCommands.Justification;
import net.nmoncho.faradn.printer.text.BoxDrawing;
import net.nmoncho.faradn.printer.text.TextWrapper;

/**
 * Renders the intermediate representation ({@code List<Block>}) into StarPRNT
 * bytes for a Star Micronics printer (the TSP143IV), the native counterpart to
 * {@link net.nmoncho.faradn.printer.EscPosRenderer}. It mirrors that renderer's
 * structure exactly - the same block dispatch, paragraph fan-out, style
 * diff-only state machine and end-of-job framing - and differs only in the
 * byte-emitting layer:
 * <ul>
 * <li>bold is two opcodes ({@code ESC E}/{@code ESC F}), not
 * {@code ESC E n};</li>
 * <li>invert is {@code ESC 4}/{@code ESC 5}, size is {@code ESC i}, font is
 * {@code ESC RS F}, alignment is {@code ESC GS a}, feed is {@code ESC a}, cut
 * is
 * {@code ESC d}, code-page switching is {@code ESC GS t}, and images use
 * {@code ESC GS S};</li>
 * <li>italic has no StarPRNT opcode, so it is a no-op;</li>
 * <li>page mode is unsupported: a {@link Canvas} block throws
 * {@link UnsupportedBlockException} (the TSP143IV is a receipt-flow target and
 * {@code ESC GS S} is disabled in page mode).</li>
 * </ul>
 * <p>
 * <strong>Thread-safety:</strong> instances are immutable (only the final
 * profile is held) and each {@link #render(List)} call is self-contained, so a
 * single renderer is safe to share across threads.
 */
public final class StarPrntRenderer implements Renderer {

  private static final Logger log = LoggerFactory.getLogger(StarPrntRenderer.class);

  private static final String RULE_CHARACTER = "-";
  private static final int END_OF_JOB_FEED_LINES = 4;
  private static final int TABLE_COLUMN_GUTTER = 1;

  private final PrinterProfile profile;

  public StarPrntRenderer(PrinterProfile profile) {
    if (profile == null) {
      throw new IllegalArgumentException("profile must not be null");
    }
    this.profile = profile;
  }

  /** The profile this renderer targets. */
  public PrinterProfile profile() {
    return profile;
  }

  /**
   * Renders a block sequence into a complete StarPRNT print job.
   *
   * @param blocks
   *        the intermediate representation, in reading order
   * @return the StarPRNT byte stream to send to the printer
   * @throws net.nmoncho.faradn.UnsupportedBlockException
   *         if a block has no StarPRNT renderer (e.g. a page-mode {@link Canvas})
   */
  @Override
  public byte[] render(List<Block> blocks) {
    log.debug("Rendering {} block(s) for Star profile [{}] (default code page {})", blocks.size(), profile.name(),
        profile.codePage().id());
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    out.writeBytes(new byte[] { Code.ESC, 0x40 }); // ESC @: initialize
    // ESC @ reverts the code page to the memory-switch default (not a fixed page),
    // so select the profile's page explicitly up front (ESC GS t n).
    out.writeBytes(StarCodePageEncoder.selectPage(profile.codePage().id()));

    // Text is encoded through this: it starts on the profile's default page and
    // switches inline (ESC GS t) among the profile's pages for glyphs outside it.
    final CodePageEncoder enc = StarCodePageEncoder.of(out, profile.codePage(), profile.codePages());

    // ESC @ resets the printer to exactly INITIAL, so that is where the tracked
    // "already applied" style starts.
    ComputedStyle current = ComputedStyle.INITIAL;

    for (Block block : blocks) {
      current = renderBlock(out, enc, current, block);
    }

    // Avoid double cutting if the job already ends with a cut.
    final boolean endsWithCut = !blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof Cut;
    endOfJob(out, endsWithCut);

    final byte[] job = out.toByteArray();
    log.debug("Rendered {} block(s) to {} StarPRNT byte(s)", blocks.size(), job.length);
    return job;
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
      out.writeBytes(StarPrintCommands.PRINT_AND_FEED_LINES.getCode(Lines.of(feed.lines()))); // ESC a n
      return current;
    } else if (block instanceof Cut cut) {
      out.writeBytes(cutCommand(cut.partial()));
      return current;
    } else if (block instanceof Drawer drawer) {
      out.writeBytes(drawerCommand(drawer.pin()));
      return current;
    } else if (block instanceof ImageBlock image) {
      return renderImage(out, current, image);
    } else if (block instanceof Barcode barcode) {
      return renderBarcode(out, current, barcode);
    } else if (block instanceof Table table) {
      return renderTable(out, enc, current, table);
    } else if (block instanceof Canvas canvas) {
      // Page mode is not a StarPRNT receipt-flow feature (ESC GS S is disabled in
      // page mode), so a positioned region has no StarPRNT rendering.
      throw new UnsupportedBlockException(canvas);
    } else if (block instanceof Box box) {
      return renderBox(out, enc, current, box);
    } else if (block instanceof LeaderLine leader) {
      return renderLeaderLine(out, enc, current, leader);
    } else if (block instanceof Space space) {
      renderSpace(out, space);
      return current;
    } else {
      throw new UnsupportedBlockException(block);
    }
  }

  /**
   * Feeds the paper by a block-level margin in dots. {@code ESC I n} micro-feeds
   * {@code n} dots (n/8&nbsp;mm at 203&nbsp;dpi), so it is dot-precise without a
   * motion-unit pin.
   */
  private void renderSpace(ByteArrayOutputStream out, Space space) {
    out.writeBytes(StarLineSpacingCommands.MICRO_FEED.getCode(new Amount(Math.min(255, space.dots())))); // ESC I n
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
        out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
      }
      current = applyAlignment(out, current, Alignment.RIGHT);
      current = emitRuns(out, enc, current, leader.right());
      current = clearInlineStyle(out, current);
      out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
      return current;
    }

    current = applyAlignment(out, current, Alignment.LEFT);
    current = emitRuns(out, enc, current, leader.left());
    current = clearInlineStyle(out, current);
    enc.emit(String.valueOf(leader.fill()).repeat(gap));
    current = emitRuns(out, enc, current, leader.right());
    current = clearInlineStyle(out, current);
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
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
    // A reverse-video section header: ink the whole line, so it takes its own path.
    if (paragraph.filled()) {
      return renderBanner(out, enc, current, paragraph);
    }
    final Border border = paragraph.border();
    // Side borders frame each line with │ and shrink the content, so they take
    // the box path; top/bottom-only borders stay full-width rules.
    if (border.left() || border.right()) {
      return renderBoxedParagraph(out, enc, current, paragraph, border);
    }
    // Indentation narrows the wrap and pads each line (hanging for lists).
    if (!paragraph.layout().isNone()) {
      return renderIndentedParagraph(out, enc, current, paragraph);
    }

    current = applyAlignment(out, current, paragraph.alignment());
    if (border.top()) {
      current = emitHorizontalBorder(out, enc, current, border.style());
    }

    final int extraFeed = extraLineFeedDots(paragraph);
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
      feedLine(out, extraFeed);
    }

    if (border.bottom()) {
      current = emitHorizontalBorder(out, enc, current, border.style());
    }
    return current;
  }

  /**
   * Renders a reverse-video section header: each wrapped line is padded to the
   * full paper width under invert ({@code ESC 4}) so the whole line is inked
   * (a solid black bar), with the label placed by {@link Paragraph#alignment()}.
   * The padding spaces carry invert too - unlike {@link #emitCell}, which clears
   * style for its pad - so there are no white gaps at the ends of the bar.
   */
  private ComputedStyle renderBanner(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Paragraph paragraph) {
    final int columns = effectiveColumns(paragraph.runs());
    final ComputedStyle inked = new ComputedStyle(false, false, 1, 1, current.alignment(), true); // plain, invert on
    final Alignment alignment = paragraph.alignment();

    current = applyAlignment(out, current, Alignment.LEFT); // full-width line; the label is placed by padding
    final int extraFeed = extraLineFeedDots(paragraph);
    final List<List<TextRun>> lines = TextWrapper.wrap(paragraph.runs(), columns);
    for (int i = 0; i < lines.size(); i++) {
      final int pad = Math.max(0, columns - displayWidth(lines.get(i)));
      final int leftPad = switch (alignment) {
        case RIGHT -> pad;
        case CENTER -> pad / 2;
        default -> 0;
      };
      final int rightPad = pad - leftPad;

      current = applyInlineStyle(out, current, inked); // ink the left pad
      if (leftPad > 0) {
        enc.emit(" ".repeat(leftPad));
      }
      for (TextRun segment : lines.get(i)) {
        current = applyInlineStyle(out, current, segment.style());
        enc.emit(segment.text());
      }
      if (rightPad > 0) {
        current = applyInlineStyle(out, current, inked); // ink the right pad (drops any bold from the last run)
        enc.emit(" ".repeat(rightPad));
      }
      if (i == lines.size() - 1) {
        current = clearInlineStyle(out, current); // invert off at the end of the bar
      }
      feedLine(out, extraFeed);
    }
    return current;
  }

  /**
   * Renders an indented paragraph: each line padded on the left by the indent
   * ({@code firstLineIndent} extra on the first line, or negative for a hanging
   * indent) and wrapped to the narrowed width. Left-aligned content emits its
   * runs directly (so a single, unwrapped line matches an un-indented paragraph
   * byte for byte); other alignments position within the content width.
   */
  private ComputedStyle renderIndentedParagraph(ByteArrayOutputStream out, CodePageEncoder enc,
      ComputedStyle current, Paragraph paragraph) {
    final BlockLayout layout = paragraph.layout();
    final int columns = effectiveColumns(paragraph.runs());
    final int firstPad = Math.max(0, layout.leftIndent() + layout.firstLineIndent());
    final int restPad = layout.leftIndent();
    final int firstWidth = Math.max(1, columns - firstPad - layout.rightIndent());
    final int restWidth = Math.max(1, columns - restPad - layout.rightIndent());

    current = applyAlignment(out, current, Alignment.LEFT); // the block is left on paper; indent is spaces
    final int extraFeed = extraLineFeedDots(paragraph);
    final List<List<TextRun>> lines = TextWrapper.wrap(paragraph.runs(), firstWidth, restWidth);
    for (int i = 0; i < lines.size(); i++) {
      final int pad = (i == 0) ? firstPad : restPad;
      current = clearInlineStyle(out, current);
      if (pad > 0) {
        enc.emit(" ".repeat(pad));
      }
      if (paragraph.alignment() == Alignment.LEFT) {
        current = emitRuns(out, enc, current, lines.get(i));
      } else {
        current = emitCell(out, enc, current, lines.get(i), (i == 0) ? firstWidth : restWidth, paragraph.alignment());
      }
      if (i == lines.size() - 1) {
        current = clearInlineStyle(out, current);
      }
      feedLine(out, extraFeed);
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
   * the side rails, other blocks rendered plainly between the edges), then a
   * bottom edge.
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
      } else if (child instanceof Space space) {
        current = emitFramedBlank(out, enc, current, space, border, drawing, contentWidth); // padding inside the box
      } else {
        current = renderBlock(out, enc, current, child); // non-paragraph child: no side rails (v1)
      }
    }
    current = emitBoxEdge(out, enc, current, drawing, border, contentWidth, false);
    return current;
  }

  /**
   * Emits vertical padding inside a box as blank framed lines, so the side rails
   * stay unbroken. The dot amount is rounded to whole lines (at the default 1/6"
   * line advance), since a framed blank is line-granular.
   */
  private ComputedStyle emitFramedBlank(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Space space, Border border, BoxDrawing box, int contentWidth) {
    final int lineAdvance = Math.max(1, profile.dpi() / 6); // ~default line spacing in dots
    final int lines = Math.max(1, Math.round((float) space.dots() / lineAdvance));
    for (int i = 0; i < lines; i++) {
      current = clearInlineStyle(out, current);
      if (border.left()) {
        enc.emit(box.vertical());
      }
      enc.emit(" ".repeat(contentWidth));
      if (border.right()) {
        enc.emit(box.vertical());
      }
      out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
    }
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
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * Emits a paragraph's wrapped lines framed by the box's side rails ({@code │}),
   * padded to {@code contentWidth}.
   */
  private ComputedStyle emitFramedParagraph(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current,
      Paragraph paragraph, Border border, BoxDrawing box, int contentWidth) {
    final int extraFeed = extraLineFeedDots(paragraph);
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
      feedLine(out, extraFeed);
    }
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
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * The extra per-line feed, in dots, that a paragraph's {@code line-height}
   * needs on top of the printer's default line pitch - or {@code 0} for the
   * default (or a line-height at/below the default).
   * <p>
   * StarPRNT has no "set line spacing to <em>n</em> dots" command: {@code ESC z}
   * only selects 3&nbsp;mm or 4&nbsp;mm, and {@code ESC 3 n} does not exist. So
   * an
   * arbitrary line-height is emulated by leaving the default pitch alone and
   * adding a one-time {@code ESC I} dot feed after each line to reach the target
   * (see {@link #feedLine}). Feeds only add, so a line-height <em>tighter</em>
   * than the default pitch cannot be honoured (it clamps to the default) - the
   * dot feeds only loosen.
   */
  private int extraLineFeedDots(Paragraph paragraph) {
    final OptionalInt target = paragraph.runs().get(0).style().lineHeight()
        .resolveDots(textCellHeightDots(paragraph), profile.dpi());
    if (target.isEmpty()) {
      return 0;
    }
    // The default line pitch left unchanged: ESC 0 / the Spec.1 default is 3 mm.
    final int defaultPitch = Math.max(1, Math.round(3.0f / 25.4f * profile.dpi())); // ~24 dots at 203 dpi
    return Math.max(0, Math.min(255, target.getAsInt() - defaultPitch));
  }

  /**
   * Feeds one line: a line feed at the default pitch, then (for a looser
   * line-height) an extra {@code ESC I n} dot feed to pad to the target.
   */
  private void feedLine(ByteArrayOutputStream out, int extraDots) {
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
    if (extraDots > 0) {
      out.writeBytes(StarLineSpacingCommands.MICRO_FEED.getCode(new Amount(extraDots))); // ESC I n
    }
  }

  private ComputedStyle renderRule(ByteArrayOutputStream out, CodePageEncoder enc, ComputedStyle current) {
    current = clearInlineStyle(out, current);
    enc.emit(RULE_CHARACTER.repeat(profile.columns()));
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
    return current;
  }

  private ComputedStyle renderImage(ByteArrayOutputStream out, ComputedStyle current, ImageBlock image) {
    warnIfImageUnsupported();
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, image.alignment());
    out.writeBytes(StarRasterizer.raster(image.image().raster(), profile.dotsPerLine())); // ESC GS S
    return current;
  }

  private ComputedStyle renderBarcode(ByteArrayOutputStream out, ComputedStyle current, Barcode barcode) {
    warnIfBarcodeUnsupported(barcode.symbology());
    current = clearInlineStyle(out, current);
    current = applyAlignment(out, current, barcode.alignment());
    out.writeBytes(StarBarcodeCommands.encode(barcode.symbology(), barcode.data(), barcode.options()));
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
    return current;
  }

  /**
   * Logs a warning when a feature is emitted to a profile whose capability data
   * says the printer does not support it. The bytes are still emitted (the
   * profile data may be incomplete), but a device that truly lacks the feature
   * prints garbage, so the mismatch is surfaced rather than hidden.
   */
  private void warnIfImageUnsupported() {
    if (!profile.supportsImages()) {
      log.warn("Profile [{}] does not report raster image support; the image may not print", profile.name());
    }
  }

  private void warnIfBarcodeUnsupported(String symbology) {
    final boolean supported = switch (symbology.toLowerCase()) {
      case "qr", "qrcode" -> profile.supportsQrCode();
      case "pdf417" -> profile.supportsPdf417();
      default -> profile.supportsBarcodes();
    };
    if (!supported) {
      log.warn("Profile [{}] does not report support for barcode [{}]; it may not print", profile.name(), symbology);
    }
  }

  /**
   * The height in dots of the tallest character cell in a paragraph, used to
   * resolve a relative {@code line-height} to dots. Derived from the font's
   * on-paper character width (cells are ~2:1) and scaled by the run's height
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
        out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
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
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());

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
        out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
      }

      if (r < rows.size() - 1) {
        current = clearInlineStyle(out, current);
        enc.emit(horizontalRule(box, widths, box.teeRight(), box.teeLeft(), dividers[r], dividers[r + 1]));
        out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
      }
    }

    current = clearInlineStyle(out, current);
    enc.emit(horizontalRule(box, widths, box.bottomLeft(), box.bottomRight(), dividers[rows.size() - 1], noDivider));
    out.writeBytes(StarPrintCommands.LINE_FEED.getCode());
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
   * the single-column cells in it, then the leftover is handed out proportionally
   * to fill the line (or content is shrunk proportionally when it overflows the
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
   * Alignment is a whole-line property ({@code ESC GS a}), emitted once per
   * block.
   */
  private ComputedStyle applyAlignment(ByteArrayOutputStream out, ComputedStyle current, Alignment alignment) {
    if (current.alignment() == alignment) {
      return current;
    }
    out.writeBytes(StarPrintPositionCommands.SELECT_JUSTIFICATION.getCode(justification(alignment)));
    return withAlignment(current, alignment);
  }

  /**
   * Emits the commands to move from {@code current} to {@code target} for the
   * per-run attributes, leaving alignment untouched. Only the attributes that
   * differ produce output. Bold is a two-opcode pair (no parameter), size clamps
   * to the model's 6x limit, and italic has no StarPRNT opcode so it is a no-op
   * (still tracked, so it never re-emits).
   */
  private ComputedStyle applyInlineStyle(ByteArrayOutputStream out, ComputedStyle current, ComputedStyle target) {
    if (current.bold() != target.bold()) {
      out.writeBytes(
          target.bold() ? StarCharacterCommands.BOLD_ON.getCode() : StarCharacterCommands.BOLD_OFF.getCode());
    }
    if (current.underline() != target.underline()) {
      out.writeBytes(target.underline()
          ? StarCharacterCommands.UNDERLINE.turnOn()
          : StarCharacterCommands.UNDERLINE.turnOff());
    }
    if (current.widthMultiple() != target.widthMultiple() || current.heightMultiple() != target.heightMultiple()) {
      out.writeBytes(StarCharacterCommands.SELECT_CHARACTER_SIZE
          .getCode(new StarCharacterSize(Math.min(6, target.widthMultiple()), Math.min(6, target.heightMultiple()))));
    }
    if (current.invert() != target.invert()) {
      out.writeBytes(target.invert()
          ? StarCharacterCommands.INVERT_ON.getCode()
          : StarCharacterCommands.INVERT_OFF.getCode());
    }
    if (current.font() != target.font()) {
      out.writeBytes(StarCharacterCommands.SELECT_FONT.getCode(new FontSlot(target.font()))); // ESC RS F n
    }
    if (current.upsideDown() != target.upsideDown()) {
      out.writeBytes(target.upsideDown()
          ? StarCharacterCommands.UPSIDE_DOWN_ON.getCode() // SI
          : StarCharacterCommands.UPSIDE_DOWN_OFF.getCode()); // DC2
    }
    // italic, double-strike and smoothing have no StarPRNT command, so nothing is
    // emitted for them (still tracked, so they never re-emit).
    return new ComputedStyle(target.bold(), target.underline(), target.widthMultiple(), target.heightMultiple(),
        current.alignment(), target.invert(), target.font(), target.italic(),
        target.doubleStrike(), target.upsideDown(), target.smoothing());
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
    out.writeBytes(StarPrintCommands.PRINT_AND_FEED_LINES.getCode(Lines.of(END_OF_JOB_FEED_LINES))); // ESC a n
    if (profile.supportsCut()) {
      out.writeBytes(cutCommand(true));
    }
  }

  private static byte[] cutCommand(boolean partial) {
    return (partial ? StarMechanismCommands.PARTIAL_CUT : StarMechanismCommands.FULL_CUT).getCode();
  }

  private static byte[] drawerCommand(int pin) {
    return (pin == 5 ? StarMechanismCommands.DRAWER_KICK_2 : StarMechanismCommands.DRAWER_KICK_1).getCode();
  }

  private static ComputedStyle withAlignment(ComputedStyle style, Alignment alignment) {
    return new ComputedStyle(style.bold(), style.underline(), style.widthMultiple(), style.heightMultiple(),
        alignment, style.invert(), style.font(), style.italic(),
        style.doubleStrike(), style.upsideDown(), style.smoothing());
  }

  private static Justification justification(Alignment alignment) {
    return switch (alignment) {
      case LEFT -> Justification.LEFT;
      case CENTER -> Justification.CENTER;
      case RIGHT -> Justification.RIGHT;
    };
  }
}
