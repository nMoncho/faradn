//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import net.nmoncho.faradn.BarcodeException;
import net.nmoncho.faradn.document.BarcodeOptions;

/**
 * Builds EPL2 barcode command strings, mirroring the neutral
 * {@code encode(..., symbology, data, options)} surface of the other backends.
 * EPL field commands carry their own {@code x,y}, so the origin is passed in.
 * <ul>
 * <li><b>1D</b> is {@code B x,y,rot,type,narrow,wide,height,B|N,"data"} with a
 * type-code string per symbology.</li>
 * <li><b>PDF417</b> is the lowercase {@code b x,y,P,"data"} command.</li>
 * <li><b>QR</b> is {@code b x,y,Q,"data"}, which EPL2 documents as
 * <em>Japanese printer models only</em>; on a general ZD421 prefer a ZPL
 * profile. It is emitted (with a caller-side warning) rather than dropped.</li>
 * </ul>
 * Verified against Zebra's EPL2 Programmer's Manual (B command + type table;
 * the
 * 2D {@code b} selectors). HRI is on/off only and always prints below, so
 * {@code ABOVE}/{@code BOTH} degrade to below.
 * <p>
 * <strong>Not part of the public API.</strong>
 */
public final class EplBarcodes {

  private static final int DEFAULT_NARROW = 2; // narrow bar width in dots
  private static final int WIDE_RATIO = 3; // wide = narrow x ratio
  private static final int MAX_WIDE = 30;

  private EplBarcodes() {
  }

  private enum Symbology {
    CODE128, CODE39, CODE93, EAN13, EAN8, UPCA, UPCE, ITF, CODABAR;
  }

  /**
   * Encodes a barcode at {@code (x, y)}.
   *
   * @param xDots
   *        field origin x
   * @param yDots
   *        field origin y
   * @param rotation
   *        EPL rotation (0..3); ignored by the 2D {@code b} command
   * @param symbology
   *        symbology name, e.g. {@code "code128"}, {@code "pdf417"}, {@code "qr"}
   * @param data
   *        the barcode payload
   * @param options
   *        height, module size and HRI position; module {@code 0} means default
   * @return the EPL command string (no line terminator)
   * @throws BarcodeException
   *         if the symbology is unknown or the data is invalid for it
   */
  public static String encode(int xDots, int yDots, int rotation, String symbology, String data,
      BarcodeOptions options) {
    final String key = normalize(symbology);
    final int module = options.moduleSize();
    return switch (key) {
      case "qr", "qrcode" -> twoDimensional(xDots, yDots, 'Q', data);
      case "pdf417" -> twoDimensional(xDots, yDots, 'P', data);
      default -> oneDimensional(xDots, yDots, rotation, symbologyOf(key), data, options.heightDots(),
          module == 0 ? DEFAULT_NARROW : module, options.hri());
    };
  }

  /**
   * Whether a symbology renders as a 2D matrix (QR / PDF417) rather than 1D bars.
   *
   * @param symbology
   *        the symbology name, matched leniently
   * @return {@code true} for {@code qr}/{@code pdf417}
   */
  public static boolean isTwoDimensional(String symbology) {
    return switch (normalize(symbology == null ? "" : symbology)) {
      case "qr", "qrcode", "pdf417" -> true;
      default -> false;
    };
  }

  /**
   * Whether a symbology is QR, which is unreliable on a general (non-JP) ZD421.
   */
  public static boolean isQrCode(String symbology) {
    return switch (normalize(symbology == null ? "" : symbology)) {
      case "qr", "qrcode" -> true;
      default -> false;
    };
  }

  private static String oneDimensional(int x, int y, int rotation, Symbology symbology, String data, int height,
      int narrow, BarcodeOptions.Hri hri) {
    validate(symbology, data);
    final int wide = Math.min(MAX_WIDE, narrow * WIDE_RATIO);
    final char human = hri == BarcodeOptions.Hri.NONE ? 'N' : 'B';
    return "B" + x + "," + y + "," + rotation + "," + typeCode(symbology) + "," + narrow + "," + wide + "," + height
        + "," + human + ",\"" + EplCommands.escape(data) + "\"";
  }

  private static String twoDimensional(int x, int y, char type, String data) {
    if (data == null || data.isEmpty()) {
      throw new BarcodeException("Barcode data must not be empty");
    }
    // Basic form; 2D options (columns, ECC, origin) use EPL defaults. Note PDF417
    // centers on (x,y) by default, so exact top-left placement is a later refinement.
    return "b" + x + "," + y + "," + type + ",\"" + EplCommands.escape(data) + "\"";
  }

  private static String typeCode(Symbology symbology) {
    return switch (symbology) {
      case CODE128 -> "1";
      case CODE39 -> "3";
      case CODE93 -> "9";
      case EAN13 -> "E30";
      case EAN8 -> "E80";
      case UPCA -> "UA0";
      case UPCE -> "UE0";
      case ITF -> "2";
      case CODABAR -> "K";
    };
  }

  private static void validate(Symbology symbology, String data) {
    if (data == null || data.isEmpty()) {
      throw new BarcodeException("Barcode data must not be empty");
    }
    switch (symbology) {
      case EAN13 -> requireDigits(data, 12, 13, "EAN-13");
      case EAN8 -> requireDigits(data, 7, 8, "EAN-8");
      case UPCA -> requireDigits(data, 11, 12, "UPC-A");
      case UPCE -> requireDigits(data, 6, 8, "UPC-E");
      case ITF -> {
        requireDigits(data, 2, 255, "ITF");
        if (data.length() % 2 != 0) {
          throw new BarcodeException("ITF requires an even number of digits, got " + data.length());
        }
      }
      default -> requireAscii(data, symbology.name());
    }
  }

  private static void requireDigits(String data, int min, int max, String label) {
    if (data.length() < min || data.length() > max) {
      throw new BarcodeException(label + " expects " + min + "-" + max + " digits, got " + data.length());
    }
    for (int i = 0; i < data.length(); i++) {
      if (!Character.isDigit(data.charAt(i))) {
        throw new BarcodeException(label + " expects digits only, got [" + data + "]");
      }
    }
  }

  private static void requireAscii(String data, String label) {
    for (int i = 0; i < data.length(); i++) {
      if (data.charAt(i) > 0x7F) {
        throw new BarcodeException(label + " expects ASCII data, got [" + data + "]");
      }
    }
  }

  private static Symbology symbologyOf(String key) {
    return switch (key) {
      case "upca" -> Symbology.UPCA;
      case "upce" -> Symbology.UPCE;
      case "ean13", "jan13" -> Symbology.EAN13;
      case "ean8", "jan8" -> Symbology.EAN8;
      case "code39" -> Symbology.CODE39;
      case "itf", "itf14", "interleaved2of5" -> Symbology.ITF;
      case "codabar", "nw7" -> Symbology.CODABAR;
      case "code93" -> Symbology.CODE93;
      case "code128" -> Symbology.CODE128;
      default -> throw new BarcodeException("Unknown barcode symbology: " + key);
    };
  }

  private static String normalize(String symbology) {
    return symbology.toLowerCase().replaceAll("[^a-z0-9]", "");
  }
}
