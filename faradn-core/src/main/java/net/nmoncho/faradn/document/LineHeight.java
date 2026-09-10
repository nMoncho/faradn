//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

import java.util.OptionalInt;

/**
 * A parsed CSS {@code line-height}, resolved to an ESC/POS line spacing (dots)
 * only at render time.
 * <p>
 * The common unitless form ({@code line-height: 1.5}) and {@code %} are
 * relative
 * to the font's cell height, which the HTML→IR stage doesn't know, so this
 * stays
 * symbolic in the IR and the renderer resolves it against the profile's cell
 * height and dpi. Absolute lengths ({@code px}/{@code mm}/{@code cm}) carry
 * their
 * unit and are converted with the printer's dpi.
 *
 * @param kind
 *        how {@code value} is interpreted
 * @param value
 *        a multiple of the cell height ({@link Kind#FACTOR}) or a length in the
 *        unit named by {@code kind}; unused for {@link Kind#NORMAL}
 */
public record LineHeight(Kind kind, double value) {

  /** How a {@link LineHeight}'s {@code value} maps to a line spacing. */
  public enum Kind {
    /** Use the printer's default spacing ({@code ESC 2}). */
    NORMAL,
    /** A multiple of the font cell height (unitless number or {@code %}). */
    FACTOR,
    /** An absolute length in pixels (1 px = 1 dot). */
    PX,
    /** An absolute length in millimetres (via dpi). */
    MM,
    /** An absolute length in centimetres (via dpi). */
    CM
  }

  /** Millimetres per inch, for converting physical lengths to dots. */
  private static final double MM_PER_INCH = 25.4;

  /**
   * The largest line spacing {@code ESC 3 n} can express (n is a single byte).
   */
  private static final int MAX_SPACING_DOTS = 255;

  /** {@code line-height: normal} - the printer default. */
  public static final LineHeight NORMAL = new LineHeight(Kind.NORMAL, 0);

  public LineHeight {
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
  }

  /**
   * Parses a CSS {@code line-height} value. A unit-less number and {@code %} are
   * multiples of the font size; {@code px}/{@code mm}/{@code cm} are absolute.
   * {@code normal}, blank, and unparseable input all yield {@link #NORMAL}.
   *
   * @param raw
   *        the CSS value, e.g. {@code "1.5"}, {@code "150%"}, {@code "30px"};
   *        may be {@code null}
   * @return the parsed line height, never {@code null}
   */
  public static LineHeight parse(String raw) {
    if (raw == null) {
      return NORMAL;
    }
    final String v = raw.strip().toLowerCase();
    if (v.isEmpty() || v.equals("normal")) {
      return NORMAL;
    }
    try {
      if (v.endsWith("%")) {
        return new LineHeight(Kind.FACTOR, Double.parseDouble(v.substring(0, v.length() - 1).strip()) / 100.0);
      } else if (v.endsWith("px")) {
        return new LineHeight(Kind.PX, Double.parseDouble(v.substring(0, v.length() - 2).strip()));
      } else if (v.endsWith("mm")) {
        return new LineHeight(Kind.MM, Double.parseDouble(v.substring(0, v.length() - 2).strip()));
      } else if (v.endsWith("cm")) {
        return new LineHeight(Kind.CM, Double.parseDouble(v.substring(0, v.length() - 2).strip()));
      }
      return new LineHeight(Kind.FACTOR, Double.parseDouble(v)); // unit-less multiple
    } catch (NumberFormatException ignored) {
      return NORMAL;
    }
  }

  /**
   * Resolves to a line spacing in dots for {@code ESC 3 n}, clamped to a single
   * byte {@code [0, 255]}. Empty for {@link Kind#NORMAL}, meaning "leave the
   * printer default" ({@code ESC 2}).
   *
   * @param cellHeightDots
   *        the font cell height in dots, the reference for {@link Kind#FACTOR}
   * @param dpi
   *        the printer resolution, for {@code mm}/{@code cm}
   * @return the spacing in dots, or empty for the default
   */
  public OptionalInt resolveDots(int cellHeightDots, int dpi) {
    final double dots = switch (kind) {
      case NORMAL -> Double.NaN;
      case FACTOR -> value * cellHeightDots;
      case PX -> value;
      case MM -> value * dpi / MM_PER_INCH;
      case CM -> value * 10 * dpi / MM_PER_INCH;
    };
    if (Double.isNaN(dots)) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(Math.max(0, Math.min(MAX_SPACING_DOTS, (int) Math.round(dots))));
  }
}
