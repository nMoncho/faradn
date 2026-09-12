//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import java.nio.charset.StandardCharsets;
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
 * Renders the positioned-label IR ({@code Canvas}/{@code Placement}) into ZPL
 * II
 * for a Zebra printer (the ZD421). It is the structural mirror of the receipt
 * renderers: each {@link Canvas} block becomes one label framed
 * {@code ^XA ... ^XZ}, and the receipt-flow blocks (a top-level
 * {@link Paragraph}, {@code Table}, {@code Rule}, ...) have no label mapping
 * and
 * throw {@link UnsupportedBlockException}. Author a label as a sized
 * {@code <body>} or a positioned container so it reaches the renderer as a
 * {@code Canvas}.
 * <p>
 * Each {@link Placement} is positioned with {@code ^FO} (upper-left, so no
 * baseline hack) and its {@link Placeable} content mapped natively: a
 * {@link Paragraph} to one {@code ^A}/{@code ^FD} field per run per wrapped
 * line
 * (segmented by {@link LabelLayout}); a {@link Barcode} to a native
 * {@link ZplBarcodes} field; an {@link ImageBlock} to a {@link ZplRasterizer}
 * {@code ^GFA} graphic; and a paragraph {@link Border} to a real {@code ^GB}
 * rectangle. Whole-canvas and per-placement rotation are applied by
 * transforming
 * coordinates ({@link LabelLayout#rotate}) and setting each field's orientation
 * letter.
 * <p>
 * <strong>Scope (v1):</strong> media tracking/type ({@code ^MN}/{@code ^MT})
 * are profile-driven and land with the ZD421 profiles; darkness/speed are not
 * emitted here. Text is placed with the resident scalable font ({@code ^A0}),
 * sized in dots from the profile's font metrics; ZPL has no bold/italic
 * attribute, so those style bits do not change the glyphs (a documented
 * fidelity gap). The 90/270 rotation anchor is confirmed on hardware (see
 * {@code PLAN_ZEBRA_ZD421.md}).
 * <p>
 * <strong>Thread-safety:</strong> instances are immutable (only the profile is
 * held) and each {@link #render(List)} call is self-contained.
 */
public final class ZplRenderer implements Renderer {

  private static final Logger log = LoggerFactory.getLogger(ZplRenderer.class);

  /**
   * The resident scalable font, sized by the {@code ^A0} height/width in dots.
   */
  private static final char SCALABLE_FONT = '0';

  /** Cell aspect: a character cell is about twice as tall as it is wide. */
  private static final int CELL_ASPECT = 2;

  private static final int BORDER_THICKNESS_DOTS = 2;
  private static final int BORDER_DOUBLE_INSET_DOTS = 4;

  private final PrinterProfile profile;

  public ZplRenderer(PrinterProfile profile) {
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
   * Renders a block sequence into a complete ZPL job (one {@code ^XA ... ^XZ}
   * label per {@link Canvas}).
   *
   * @param blocks
   *        the intermediate representation, in reading order
   * @return the ZPL byte stream (UTF-8, matching the emitted {@code ^CI28})
   * @throws UnsupportedBlockException
   *         if a block is not a {@link Canvas} (the label backend does not render
   *         the receipt flow)
   */
  @Override
  public byte[] render(List<Block> blocks) {
    final StringBuilder job = new StringBuilder();
    for (Block block : blocks) {
      if (block instanceof Canvas canvas) {
        renderCanvas(job, canvas);
      } else {
        throw new UnsupportedBlockException(block);
      }
    }
    log.debug("Rendered {} block(s) into {} ZPL bytes for profile [{}]", blocks.size(), job.length(), profile.name());
    return job.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void renderCanvas(StringBuilder job, Canvas canvas) {
    final int width = Math.min(canvas.widthDots(), profile.dotsPerLine());
    job.append(ZplCommands.START);
    job.append(ZplCommands.UNITS_DOTS);
    job.append(ZplCommands.encodingUtf8());
    job.append(ZplCommands.labelHome(0, 0));
    job.append(ZplCommands.printWidth(width));
    job.append(ZplCommands.labelLength(canvas.heightDots()));
    for (Placement placement : canvas.placements()) {
      renderPlacement(job, canvas, placement);
    }
    job.append(ZplCommands.END);
  }

  private void renderPlacement(StringBuilder job, Canvas canvas, Placement placement) {
    // A placement may rotate independently of the canvas; a null rotation inherits it.
    final Canvas.Direction dir = placement.rotation() != null ? placement.rotation() : canvas.direction();
    final Placeable content = placement.content();
    if (content instanceof Paragraph paragraph) {
      renderParagraph(job, canvas, placement, dir, paragraph);
    } else if (content instanceof Barcode barcode) {
      renderBarcode(job, canvas, placement, dir, barcode);
    } else if (content instanceof ImageBlock image) {
      renderImage(job, canvas, placement, dir, image);
    }
  }

  private void renderParagraph(StringBuilder job, Canvas canvas, Placement placement, Canvas.Direction dir,
      Paragraph paragraph) {
    final int charWidth = LabelLayout.charWidthDots(profile.dotsPerLine(), profile.defaultFont().columns());
    final int lineHeight = CELL_ASPECT * charWidth * maxHeightMultiple(paragraph);
    final int wrapWidth = Math.max(1, canvas.widthDots() - placement.xDots());
    final int columns = LabelLayout.columnsForWidth(wrapWidth, charWidth);
    final List<TextSegment> segments = LabelLayout.segment(paragraph.runs(), columns, charWidth, lineHeight);

    if (paragraph.border().any()) {
      renderBorder(job, canvas, placement, dir, paragraph.border(), boxWidth(segments, charWidth),
          boxHeight(segments, lineHeight));
    }

    for (TextSegment segment : segments) {
      final Point origin = LabelLayout.rotate(placement.xDots() + segment.dxDots(),
          placement.yDots() + segment.dyDots(),
          canvas.widthDots(), canvas.heightDots(), dir);
      final TextRun run = segment.run();
      job.append(ZplCommands.fieldOrigin(origin.xDots(), origin.yDots()));
      job.append(ZplCommands.font(SCALABLE_FONT, dir, CELL_ASPECT * charWidth * run.style().heightMultiple(),
          charWidth * run.style().widthMultiple()));
      job.append(ZplCommands.fieldData(run.text()));
    }
  }

  private void renderBarcode(StringBuilder job, Canvas canvas, Placement placement, Canvas.Direction dir,
      Barcode barcode) {
    warnIfBarcodeUnsupported(barcode.symbology());
    final Point origin = LabelLayout.rotate(placement.xDots(), placement.yDots(), canvas.widthDots(),
        canvas.heightDots(), dir);
    job.append(ZplCommands.fieldOrigin(origin.xDots(), origin.yDots()));
    job.append(ZplBarcodes.encode(barcode.symbology(), barcode.data(), barcode.options(), dir));
  }

  private void renderImage(StringBuilder job, Canvas canvas, Placement placement, Canvas.Direction dir,
      ImageBlock image) {
    if (!profile.supportsImages()) {
      log.warn("Profile [{}] reports no image support; emitting the graphic anyway", profile.name());
    }
    // ^GF graphics carry no field orientation, so a rotated canvas moves the
    // image's origin but does not rotate its pixels (a documented v1 limitation).
    final Point origin = LabelLayout.rotate(placement.xDots(), placement.yDots(), canvas.widthDots(),
        canvas.heightDots(), dir);
    final int maxWidth = Math.max(1, Math.min(profile.dotsPerLine(), canvas.widthDots() - placement.xDots()));
    job.append(ZplCommands.fieldOrigin(origin.xDots(), origin.yDots()));
    job.append(ZplRasterizer.graphicField(image.image().raster(), maxWidth));
  }

  /**
   * Draws a paragraph's border as native geometry: a full (four-sided) border is
   * one {@code ^GB} box (two concentric boxes for {@code DOUBLE}); a partial
   * border is one {@code ^GB} line per enabled side. Positions are transformed
   * for the placement direction; box extents are not swapped for 90/270 (a
   * documented v1 limitation).
   */
  private void renderBorder(StringBuilder job, Canvas canvas, Placement placement, Canvas.Direction dir, Border border,
      int width, int height) {
    final int x = placement.xDots();
    final int y = placement.yDots();
    final int t = BORDER_THICKNESS_DOTS;
    if (border.top() && border.right() && border.bottom() && border.left()) {
      box(job, canvas, dir, x, y, width, height, t);
      if (border.style() == Border.Style.DOUBLE) {
        final int inset = BORDER_DOUBLE_INSET_DOTS;
        box(job, canvas, dir, x + inset, y + inset, Math.max(1, width - 2 * inset), Math.max(1, height - 2 * inset), t);
      }
      return;
    }
    if (border.top()) {
      box(job, canvas, dir, x, y, width, t, t);
    }
    if (border.bottom()) {
      box(job, canvas, dir, x, y + height - t, width, t, t);
    }
    if (border.left()) {
      box(job, canvas, dir, x, y, t, height, t);
    }
    if (border.right()) {
      box(job, canvas, dir, x + width - t, y, t, height, t);
    }
  }

  private void box(StringBuilder job, Canvas canvas, Canvas.Direction dir, int x, int y, int width, int height,
      int thickness) {
    final Point origin = LabelLayout.rotate(x, y, canvas.widthDots(), canvas.heightDots(), dir);
    job.append(ZplCommands.fieldOrigin(origin.xDots(), origin.yDots()));
    job.append(ZplCommands.graphicBox(Math.max(1, width), Math.max(1, height), thickness));
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

  private void warnIfBarcodeUnsupported(String symbology) {
    if (!profile.supportsBarcodes()) {
      log.warn("Profile [{}] reports no barcode support; emitting [{}] anyway", profile.name(), symbology);
    }
  }
}
