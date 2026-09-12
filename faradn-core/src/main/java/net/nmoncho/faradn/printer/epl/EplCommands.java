//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import net.nmoncho.faradn.document.Canvas;

/**
 * Builds EPL2 command strings for the label setup, text, and geometry an
 * {@code EplRenderer} emits. Like {@link net.nmoncho.faradn.printer.zpl}, EPL2
 * is an ASCII command language, so this layer is string formatting; unlike ZPL
 * it is line-based (each command on its own line, the renderer terminating each
 * with a line feed) and single-byte (text is encoded in the profile's code
 * page,
 * not UTF-8).
 * <p>
 * Field commands ({@code A} text) carry their own {@code x,y} origin (there is
 * no
 * separate positioning command like ZPL {@code ^FO}). Barcodes are in
 * {@link EplBarcodes} and graphics in {@link EplRasterizer}. Commands were
 * researched against Zebra's EPL2 Programmer's Manual.
 * <p>
 * <strong>Not part of the public API.</strong>
 */
public final class EplCommands {

  /** Clear the image buffer, beginning a new label ({@code N}). */
  public static final String CLEAR_BUFFER = "N";

  private static final int MAX_DENSITY = 15;

  private EplCommands() {
  }

  /** Label width in dots, {@code q}. */
  public static String labelWidth(int dots) {
    return "q" + positive(dots, "label width");
  }

  /** Label length and inter-label gap in dots, {@code Q}. */
  public static String labelLength(int heightDots, int gapDots) {
    return "Q" + positive(heightDots, "label length") + "," + nonNegative(gapDots, "gap");
  }

  /**
   * Label length with a black-mark of the given thickness in dots,
   * {@code Q...,B}.
   */
  public static String labelLengthBlackMark(int heightDots, int markDots) {
    return "Q" + positive(heightDots, "label length") + ",B" + positive(markDots, "mark");
  }

  /** Print speed index (model-specific), {@code S}. */
  public static String speed(int speed) {
    return "S" + positive(speed, "speed");
  }

  /** Print density 0..15, {@code D}. */
  public static String density(int density) {
    if (density < 0 || density > MAX_DENSITY) {
      throw new IllegalArgumentException("density must be in [0, " + MAX_DENSITY + "], got " + density);
    }
    return "D" + density;
  }

  /**
   * Whole-label print orientation, {@code ZT} (top first) or {@code ZB} (bottom
   * first).
   */
  public static String printOrientation(boolean bottomFirst) {
    return bottomFirst ? "ZB" : "ZT";
  }

  /** Reference point (origin offset) in dots, {@code R}. */
  public static String reference(int xDots, int yDots) {
    return "R" + nonNegative(xDots, "x") + "," + nonNegative(yDots, "y");
  }

  /**
   * Hardware options, {@code O} (e.g. {@code "D"} for direct thermal, {@code ""}
   * to clear).
   */
  public static String options(String options) {
    return "O" + (options == null ? "" : options);
  }

  /**
   * Character-set selection, {@code I p1,p2,p3}: data bits (7 or 8), code page
   * (a single-byte DOS number or Windows letter) and country code. EPL2 has no
   * UTF-8, so text is single-byte in the selected page.
   */
  public static String codePage(int dataBits, String page, String country) {
    if (dataBits != 7 && dataBits != 8) {
      throw new IllegalArgumentException("dataBits must be 7 or 8, got " + dataBits);
    }
    return "I" + dataBits + "," + page + "," + country;
  }

  /** Print the label, {@code P} label sets. */
  public static String print(int labelSets) {
    return "P" + positive(labelSets, "label sets");
  }

  /**
   * A text field, {@code A x,y,rot,font,hMul,vMul,N|R,"data"}: origin (top-left,
   * in dots), rotation (0..3 = 0/90/180/270), internal font (1..5) or soft font,
   * horizontal and vertical size multipliers, normal ({@code N}) or reverse
   * ({@code R}) image, and the quoted data (quotes and backslashes escaped, line
   * breaks stripped).
   */
  public static String text(int xDots, int yDots, int rotation, int font, int horizontalMultiplier,
      int verticalMultiplier, boolean reverse, String data) {
    return "A" + nonNegative(xDots, "x") + "," + nonNegative(yDots, "y") + "," + rotation(rotation) + ","
        + positive(font, "font") + "," + positive(horizontalMultiplier, "horizontal multiplier") + ","
        + positive(verticalMultiplier, "vertical multiplier") + "," + (reverse ? 'R' : 'N') + ",\""
        + escape(data == null ? "" : data) + "\"";
  }

  /**
   * A box outline, {@code X x1,y1,thickness,x2,y2} (start corner, line thickness,
   * end corner).
   */
  public static String box(int x1, int y1, int thicknessDots, int x2, int y2) {
    return "X" + nonNegative(x1, "x1") + "," + nonNegative(y1, "y1") + "," + positive(thicknessDots, "thickness") + ","
        + nonNegative(x2, "x2") + "," + nonNegative(y2, "y2");
  }

  /**
   * A solid black rectangle (line),
   * {@code LO x,y,horizontalLength,verticalLength}.
   */
  public static String lineBlack(int xDots, int yDots, int horizontalLengthDots, int verticalLengthDots) {
    return line("LO", xDots, yDots, horizontalLengthDots, verticalLengthDots);
  }

  /** A white (erasing) rectangle, {@code LW}. */
  public static String lineWhite(int xDots, int yDots, int horizontalLengthDots, int verticalLengthDots) {
    return line("LW", xDots, yDots, horizontalLengthDots, verticalLengthDots);
  }

  /** An exclusive-or (reverse-video) rectangle, {@code LE}. */
  public static String lineXor(int xDots, int yDots, int horizontalLengthDots, int verticalLengthDots) {
    return line("LE", xDots, yDots, horizontalLengthDots, verticalLengthDots);
  }

  /** The EPL rotation parameter (0..3) for a canvas/placement direction. */
  public static int rotation(Canvas.Direction direction) {
    if (direction == null) {
      throw new IllegalArgumentException("direction must not be null");
    }
    return switch (direction) {
      case NORMAL -> 0;
      case ROTATE_90_CW -> 1;
      case ROTATE_180 -> 2;
      case ROTATE_90_CCW -> 3;
    };
  }

  /**
   * Escapes text for an EPL quoted data field: a literal {@code "} becomes
   * {@code \"} and {@code \} becomes {@code \\}; CR/LF are stripped because the
   * protocol is line-based. Package-private so {@link EplBarcodes} reuses it.
   */
  static String escape(String data) {
    final StringBuilder escaped = new StringBuilder(data.length());
    for (int i = 0; i < data.length(); i++) {
      final char c = data.charAt(i);
      if (c == '\r' || c == '\n') {
        continue;
      }
      if (c == '\\' || c == '"') {
        escaped.append('\\');
      }
      escaped.append(c);
    }
    return escaped.toString();
  }

  private static String line(String command, int x, int y, int horizontalLength, int verticalLength) {
    return command + nonNegative(x, "x") + "," + nonNegative(y, "y") + "," + positive(horizontalLength, "length") + ","
        + positive(verticalLength, "length");
  }

  private static int rotation(int rotation) {
    if (rotation < 0 || rotation > 3) {
      throw new IllegalArgumentException("rotation must be in [0, 3], got " + rotation);
    }
    return rotation;
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
