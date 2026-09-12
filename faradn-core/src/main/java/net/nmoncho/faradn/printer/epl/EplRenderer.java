//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.nmoncho.faradn.UnsupportedBlockException;
import net.nmoncho.faradn.document.Barcode;
import net.nmoncho.faradn.document.Block;
import net.nmoncho.faradn.document.Border;
import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ImageBlock;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.Placeable;
import net.nmoncho.faradn.document.Placement;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.Renderer;
import net.nmoncho.faradn.printer.label.LabelLayout;
import net.nmoncho.faradn.printer.label.LabelLayout.Point;
import net.nmoncho.faradn.printer.label.LabelLayout.TextSegment;

/**
 * Renders the positioned-label IR ({@code Canvas}/{@code Placement}) into EPL2
 * for a Zebra printer (the ZD421), the ZD421's second native label language
 * alongside {@link net.nmoncho.faradn.printer.zpl.ZplRenderer}. It shares that
 * renderer's shape - each {@link Canvas} becomes one label, the receipt-flow
 * blocks throw {@link UnsupportedBlockException}, and placements are laid out
 * and
 * rotated with {@link LabelLayout} - and differs in the byte layer:
 * <ul>
 * <li>EPL is line-based ASCII; each command is terminated with a line feed
 * (emitted here as CRLF, which contains the required LF).</li>
 * <li>text is single-byte: the whole job is built as bytes, encoded in the
 * profile's code page (EPL2 has no UTF-8), and the {@code I} command selects
 * that page;</li>
 * <li>images use {@code GW} with a bit-inverted body (EPL {@code 0} =
 * black);</li>
 * <li>QR is unreliable on a general ZD421 (EPL {@code b,Q} is Japanese-models
 * only), so it is emitted with a warning - prefer a ZPL profile for QR.</li>
 * </ul>
 * <strong>Scope (v1):</strong> media tracking maps to the {@code Q} gap; media
 * type, darkness and speed are device-config concerns and not emitted. Text
 * uses
 * a fixed internal font ({@value #EPL_FONT}) scaled by the run's size multiples
 * (EPL fonts are fixed-size, so glyphs are quantized); EPL has no bold/italic
 * attribute. The 90/270 rotation anchor is confirmed on hardware (see
 * {@code PLAN_ZEBRA_ZD421.md}).
 * <p>
 * <strong>Thread-safety:</strong> instances are immutable and each
 * {@link #render(List)} call is self-contained.
 */
public final class EplRenderer implements Renderer {

  private static final Logger log = LoggerFactory.getLogger(EplRenderer.class);

  private static final byte[] LINE_END = { 0x0D, 0x0A };
  /** A readable fixed internal font; EPL fonts 1..5 are fixed-size. */
  private static final int EPL_FONT = 3;
  private static final int CELL_ASPECT = 2;
  private static final int DEFAULT_GAP_DOTS = 24;
  private static final int BORDER_THICKNESS_DOTS = 2;
  private static final int BORDER_DOUBLE_INSET_DOTS = 4;
  private static final int EPL_MAX_HORIZONTAL_MULTIPLIER = 6; // 7 is invalid; 8 is the max

  private final PrinterProfile profile;

  public EplRenderer(PrinterProfile profile) {
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
   * Renders a block sequence into a complete EPL2 job (one label per
   * {@link Canvas}).
   *
   * @param blocks
   *        the intermediate representation, in reading order
   * @return the EPL2 byte stream (commands ASCII, text in the profile's code
   *         page)
   * @throws UnsupportedBlockException
   *         if a block is not a {@link Canvas}
   */
  @Override
  public byte[] render(List<Block> blocks) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final Charset charset = profile.codePage().charset();
    for (Block block : blocks) {
      if (block instanceof Canvas canvas) {
        renderCanvas(out, canvas, charset);
      } else {
        throw new UnsupportedBlockException(block);
      }
    }
    log.debug("Rendered {} block(s) into {} EPL bytes for profile [{}]", blocks.size(), out.size(), profile.name());
    return out.toByteArray();
  }

  private void renderCanvas(ByteArrayOutputStream out, Canvas canvas, Charset charset) {
    final int width = Math.min(canvas.widthDots(), profile.dotsPerLine());
    line(out, EplCommands.codePage(8, Integer.toString(profile.codePage().id()), "001"), charset);
    line(out, EplCommands.labelWidth(width), charset);
    line(out, labelLength(canvas.heightDots()), charset);
    line(out, EplCommands.CLEAR_BUFFER, charset);
    for (Placement placement : canvas.placements()) {
      renderPlacement(out, canvas, placement, charset);
    }
    line(out, EplCommands.print(1), charset);
  }

  private String labelLength(int heightDots) {
    return switch (profile.mediaTracking()) {
      case CONTINUOUS -> EplCommands.labelLength(heightDots, 0);
      case GAP -> EplCommands.labelLength(heightDots, DEFAULT_GAP_DOTS);
      case MARK -> EplCommands.labelLengthBlackMark(heightDots, DEFAULT_GAP_DOTS);
    };
  }

  private void renderPlacement(ByteArrayOutputStream out, Canvas canvas, Placement placement, Charset charset) {
    final Canvas.Direction dir = placement.rotation() != null ? placement.rotation() : canvas.direction();
    final Placeable content = placement.content();
    if (content instanceof Paragraph paragraph) {
      renderParagraph(out, canvas, placement, dir, paragraph, charset);
    } else if (content instanceof Barcode barcode) {
      renderBarcode(out, canvas, placement, dir, barcode, charset);
    } else if (content instanceof ImageBlock image) {
      renderImage(out, canvas, placement, dir, image);
    }
  }

  private void renderParagraph(ByteArrayOutputStream out, Canvas canvas, Placement placement, Canvas.Direction dir,
      Paragraph paragraph, Charset charset) {
    final int charWidth = LabelLayout.charWidthDots(profile.dotsPerLine(), profile.defaultFont().columns());
    final int lineHeight = CELL_ASPECT * charWidth * maxHeightMultiple(paragraph);
    final int wrapWidth = Math.max(1, canvas.widthDots() - placement.xDots());
    final int columns = LabelLayout.columnsForWidth(wrapWidth, charWidth);
    final List<TextSegment> segments = LabelLayout.segment(paragraph.runs(), columns, charWidth, lineHeight);
    final int rotation = EplCommands.rotation(dir);

    if (paragraph.border().any()) {
      renderBorder(out, canvas, placement, dir, paragraph.border(), boxWidth(segments, charWidth),
          boxHeight(segments, lineHeight), charset);
    }

    for (TextSegment segment : segments) {
      final Point origin = LabelLayout.rotate(placement.xDots() + segment.dxDots(),
          placement.yDots() + segment.dyDots(),
          canvas.widthDots(), canvas.heightDots(), dir);
      final TextRun run = segment.run();
      final int horizontal = run.style().widthMultiple() == 7 ? EPL_MAX_HORIZONTAL_MULTIPLIER
          : run.style().widthMultiple();
      line(out, EplCommands.text(origin.xDots(), origin.yDots(), rotation, EPL_FONT, horizontal,
          run.style().heightMultiple(), run.style().invert(), run.text()), charset);
    }
  }

  private void renderBarcode(ByteArrayOutputStream out, Canvas canvas, Placement placement, Canvas.Direction dir,
      Barcode barcode, Charset charset) {
    warnIfBarcodeUnsupported(barcode.symbology());
    final Point origin = LabelLayout.rotate(placement.xDots(), placement.yDots(), canvas.widthDots(),
        canvas.heightDots(), dir);
    line(out, EplBarcodes.encode(origin.xDots(), origin.yDots(), EplCommands.rotation(dir), barcode.symbology(),
        barcode.data(), barcode.options()), charset);
  }

  private void renderImage(ByteArrayOutputStream out, Canvas canvas, Placement placement, Canvas.Direction dir,
      ImageBlock image) {
    if (!profile.supportsImages()) {
      log.warn("Profile [{}] reports no image support; emitting the graphic anyway", profile.name());
    }
    // GW carries no rotation, so a rotated canvas moves the image origin but does
    // not rotate its pixels (a documented v1 limitation).
    final Point origin = LabelLayout.rotate(placement.xDots(), placement.yDots(), canvas.widthDots(),
        canvas.heightDots(), dir);
    final int maxWidth = Math.max(1, Math.min(profile.dotsPerLine(), canvas.widthDots() - placement.xDots()));
    bytes(out, EplRasterizer.graphicWrite(image.image().raster(), origin.xDots(), origin.yDots(), maxWidth));
  }

  private void renderBorder(ByteArrayOutputStream out, Canvas canvas, Placement placement, Canvas.Direction dir,
      Border border, int width, int height, Charset charset) {
    final int x = placement.xDots();
    final int y = placement.yDots();
    final int t = BORDER_THICKNESS_DOTS;
    if (border.top() && border.right() && border.bottom() && border.left()) {
      box(out, canvas, dir, x, y, width, height, t, charset);
      if (border.style() == Border.Style.DOUBLE) {
        final int inset = BORDER_DOUBLE_INSET_DOTS;
        box(out, canvas, dir, x + inset, y + inset, Math.max(1, width - 2 * inset), Math.max(1, height - 2 * inset), t,
            charset);
      }
      return;
    }
    if (border.top()) {
      lineBlack(out, canvas, dir, x, y, width, t, charset);
    }
    if (border.bottom()) {
      lineBlack(out, canvas, dir, x, y + height - t, width, t, charset);
    }
    if (border.left()) {
      lineBlack(out, canvas, dir, x, y, t, height, charset);
    }
    if (border.right()) {
      lineBlack(out, canvas, dir, x + width - t, y, t, height, charset);
    }
  }

  private void box(ByteArrayOutputStream out, Canvas canvas, Canvas.Direction dir, int x, int y, int width, int height,
      int thickness, Charset charset) {
    final Point corner1 = LabelLayout.rotate(x, y, canvas.widthDots(), canvas.heightDots(), dir);
    final Point corner2 = LabelLayout.rotate(x + width, y + height, canvas.widthDots(), canvas.heightDots(), dir);
    final int x1 = Math.min(corner1.xDots(), corner2.xDots());
    final int y1 = Math.min(corner1.yDots(), corner2.yDots());
    final int x2 = Math.max(corner1.xDots(), corner2.xDots());
    final int y2 = Math.max(corner1.yDots(), corner2.yDots());
    line(out, EplCommands.box(x1, y1, thickness, Math.max(x1 + 1, x2), Math.max(y1 + 1, y2)), charset);
  }

  private void lineBlack(ByteArrayOutputStream out, Canvas canvas, Canvas.Direction dir, int x, int y,
      int horizontalLength, int verticalLength, Charset charset) {
    final Point origin = LabelLayout.rotate(x, y, canvas.widthDots(), canvas.heightDots(), dir);
    line(out, EplCommands.lineBlack(origin.xDots(), origin.yDots(), horizontalLength, verticalLength), charset);
  }

  private void warnIfBarcodeUnsupported(String symbology) {
    if (EplBarcodes.isQrCode(symbology)) {
      log.warn("QR on EPL is Japanese-models only; a general ZD421 should use a ZPL profile (profile [{}])",
          profile.name());
    } else if (!profile.supportsBarcodes()) {
      log.warn("Profile [{}] reports no barcode support; emitting [{}] anyway", profile.name(), symbology);
    }
  }

  private void line(ByteArrayOutputStream out, String command, Charset charset) {
    out.writeBytes(command.getBytes(charset));
    out.writeBytes(LINE_END);
  }

  private void bytes(ByteArrayOutputStream out, byte[] command) {
    out.writeBytes(command);
    out.writeBytes(LINE_END);
  }

  private static int maxHeightMultiple(Paragraph paragraph) {
    int max = 1;
    for (TextRun run : paragraph.runs()) {
      max = Math.max(max, run.style().heightMultiple());
    }
    return max;
  }

  private static int boxWidth(List<TextSegment> segments, int charWidth) {
    int max = 0;
    for (TextSegment segment : segments) {
      final int right = segment.dxDots()
          + segment.run().text().length() * segment.run().style().widthMultiple() * charWidth;
      max = Math.max(max, right);
    }
    return Math.max(1, max);
  }

  private static int boxHeight(List<TextSegment> segments, int lineHeight) {
    int maxDy = 0;
    for (TextSegment segment : segments) {
      maxDy = Math.max(maxDy, segment.dyDots());
    }
    final int lines = segments.isEmpty() ? 1 : (maxDy / lineHeight) + 1;
    return lines * lineHeight;
  }
}
