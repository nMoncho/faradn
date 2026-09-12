//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.printer.MediaTracking;
import net.nmoncho.faradn.printer.MediaType;

/**
 * Builds ZPL II command strings for the label framing, media setup, text, and
 * geometry a {@code ZplRenderer} emits. Unlike the ESC/POS and StarPRNT command
 * layers (binary opcodes on the {@code printer.command} DSL), ZPL is an ASCII
 * command language, so this layer is string formatting: each method returns the
 * exact caret-prefixed command, and the renderer concatenates them and encodes
 * the whole label as bytes (UTF-8, after {@code ^CI28}).
 * <p>
 * Barcodes are in {@link ZplBarcodes} and graphics in {@link ZplRasterizer}.
 * Commands were verified against Zebra's ZPL II Programming Guide
 * ({@code zpl-zbi2-pg-en}); the page is cited per method.
 * <p>
 * <strong>Not part of the public API.</strong>
 */
public final class ZplCommands {

  /** Start of a label format (guide "^XA"). */
  public static final String START = "^XA";

  /** End of a label format (guide "^XZ"). */
  public static final String END = "^XZ";

  /** Set the measurement unit to dots (guide "^MU", p231). */
  public static final String UNITS_DOTS = "^MUd";

  private static final int MAX_DARKNESS = 30;

  private ZplCommands() {
  }

  /** A comment field, {@code ^FX ... ^FS} (guide "^FX"). */
  public static String comment(String text) {
    return "^FX" + (text == null ? "" : text) + "^FS";
  }

  /** Print width in dots, {@code ^PW} (guide p249). */
  public static String printWidth(int dots) {
    return "^PW" + positive(dots, "print width");
  }

  /** Label length in dots, {@code ^LL} (guide p218). */
  public static String labelLength(int dots) {
    return "^LL" + positive(dots, "label length");
  }

  /** Media tracking, {@code ^MN} (guide p228). */
  public static String mediaTracking(MediaTracking tracking) {
    if (tracking == null) {
      throw new IllegalArgumentException("tracking must not be null");
    }
    final char code = switch (tracking) {
      case CONTINUOUS -> 'N';
      case GAP -> 'Y';
      case MARK -> 'M';
    };
    return "^MN" + code;
  }

  /** Media/print type, {@code ^MT} (guide p230). */
  public static String mediaType(MediaType type) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    return "^MT" + (type == MediaType.DIRECT_THERMAL ? 'D' : 'T');
  }

  /** Absolute darkness 0..30, {@code ~SD} (guide p252). */
  public static String darkness(int level) {
    if (level < 0 || level > MAX_DARKNESS) {
      throw new IllegalArgumentException("darkness must be in [0, " + MAX_DARKNESS + "], got " + level);
    }
    return "~SD" + (level < 10 ? "0" : "") + level;
  }

  /** Print quantity, {@code ^PQ} (guide p244). */
  public static String quantity(int labels) {
    return "^PQ" + positive(labels, "quantity");
  }

  /** Label home (origin offset) in dots, {@code ^LH} (guide p217). */
  public static String labelHome(int xDots, int yDots) {
    return "^LH" + nonNegative(xDots, "x") + "," + nonNegative(yDots, "y");
  }

  /** Select UTF-8 input encoding, {@code ^CI28} (guide "^CI"). */
  public static String encodingUtf8() {
    return encoding(28);
  }

  /**
   * Select an international font/encoding by number, {@code ^CI} (guide "^CI").
   */
  public static String encoding(int number) {
    return "^CI" + nonNegative(number, "encoding");
  }

  /** Print the label upright, {@code ^PON} (guide p242). */
  public static String printOrientationNormal() {
    return "^PON";
  }

  /** Print the whole label inverted 180 degrees, {@code ^POI} (guide p242). */
  public static String printOrientationInverted() {
    return "^POI";
  }

  /** Field origin (upper-left of the field) in dots, {@code ^FO} (guide p147). */
  public static String fieldOrigin(int xDots, int yDots) {
    return "^FO" + nonNegative(xDots, "x") + "," + nonNegative(yDots, "y");
  }

  /**
   * The ZPL field-orientation letter for a canvas/placement direction: N=0,
   * R=90 clockwise, I=180, B=270 (guide "^A" / "^FW"). Shared with
   * {@link ZplBarcodes} for rotated barcodes.
   */
  public static char orientation(Canvas.Direction direction) {
    if (direction == null) {
      throw new IllegalArgumentException("direction must not be null");
    }
    return switch (direction) {
      case NORMAL -> 'N';
      case ROTATE_90_CW -> 'R';
      case ROTATE_180 -> 'I';
      case ROTATE_90_CCW -> 'B';
    };
  }

  /**
   * A scalable/bitmapped font selection, {@code ^Afo,h,w} (guide p12): font
   * name (A-Z or 0-9, where {@code 0} is the resident scalable font), an
   * orientation, and height/width in dots.
   */
  public static String font(char name, Canvas.Direction direction, int heightDots, int widthDots) {
    return "^A" + name + orientation(direction) + "," + positive(heightDots, "font height") + ","
        + positive(widthDots, "font width");
  }

  /** The document default font, {@code ^CFf,h,w} (guide p109). */
  public static String defaultFont(char name, int heightDots, int widthDots) {
    return "^CF" + name + "," + positive(heightDots, "font height") + "," + positive(widthDots, "font width");
  }

  /** The justification letter for an alignment: L/C/R (guide "^FB"). */
  public static char justification(Alignment alignment) {
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
    return switch (alignment) {
      case LEFT -> 'L';
      case CENTER -> 'C';
      case RIGHT -> 'R';
    };
  }

  /** Field data, {@code ^FD ... ^FS} (guide p137). */
  public static String fieldData(String text) {
    return "^FD" + (text == null ? "" : text) + "^FS";
  }

  /**
   * A field block for word-wrapped text, {@code ^FBw,l,s,j,i} (guide p137):
   * width in dots, max lines, added/removed line spacing in dots, justification
   * (L/C/R/J) and hanging indent in dots. Precedes the {@code ^FD} it wraps.
   */
  public static String fieldBlock(int widthDots, int maxLines, int lineSpacingDots, char justification,
      int hangingIndentDots) {
    return "^FB" + positive(widthDots, "field block width") + "," + positive(maxLines, "max lines") + ","
        + lineSpacingDots + "," + justification + "," + nonNegative(hangingIndentDots, "hanging indent");
  }

  /**
   * A black graphic box (or line) with default rounding, {@code ^GB} (guide
   * p155).
   */
  public static String graphicBox(int widthDots, int heightDots, int thicknessDots) {
    return graphicBox(widthDots, heightDots, thicknessDots, 'B', 0);
  }

  /**
   * A graphic box (or line, when a side collapses to the thickness),
   * {@code ^GBw,h,t,c,r} (guide p155): width, height, border thickness in dots,
   * line color (B/W) and corner rounding 0..8.
   */
  public static String graphicBox(int widthDots, int heightDots, int thicknessDots, char color, int rounding) {
    return "^GB" + positive(widthDots, "box width") + "," + positive(heightDots, "box height") + ","
        + positive(thicknessDots, "box thickness") + "," + color + "," + rounding;
  }

  private static int positive(int value, String what) {
    if (value < 1) {
      throw new IllegalArgumentException(what + " must be >= 1, got " + value);
    }
    return value;
  }

  private static int nonNegative(int value, String what) {
    if (value < 0) {
      throw new IllegalArgumentException(what + " must be >= 0, got " + value);
    }
    return value;
  }
}
