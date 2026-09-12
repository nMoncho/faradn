//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import net.nmoncho.faradn.BarcodeException;
import net.nmoncho.faradn.document.BarcodeOptions;
import net.nmoncho.faradn.document.Canvas;

/**
 * Builds ZPL II barcode command strings, mirroring the neutral
 * {@code encode(symbology, data, options)} surface of the ESC/POS and StarPRNT
 * barcode command layers so the renderer's call site stays identical. Unlike
 * those, ZPL draws barcodes as native vector fields (no rasterizing):
 * <ul>
 * <li><b>1D</b> is {@code ^BY} (module width) then the symbology command
 * ({@code ^BC}, {@code ^B3}, ...) then {@code ^FD data ^FS}.</li>
 * <li><b>QR</b> is {@code ^BQN,2,mag} then {@code ^FD<ec><A|M>,data^FS} - the
 * error-correction and input-mode switches ride in {@code ^FD}, not
 * {@code ^BQ}.</li>
 * <li><b>PDF417</b> is {@code ^BY} then {@code ^B7N} then {@code ^FD data ^FS}
 * (rows/columns/security left at ZPL defaults).</li>
 * </ul>
 * Verified against Zebra's ZPL II Programming Guide (barcode commands; "^BY"
 * p103; "^BQ" QR pp118-122). HRI {@code BOTH} has no ZPL expression on linear
 * symbols and degrades to {@code BELOW}.
 * <p>
 * <strong>Not part of the public API.</strong>
 */
public final class ZplBarcodes {

  private static final int DEFAULT_MODULE = 2; // ^BY module width in dots
  private static final int DEFAULT_QR_MAGNIFICATION = 2;
  private static final int DEFAULT_PDF417_MODULE = 2;

  private ZplBarcodes() {
  }

  private enum Symbology {
    CODE128, CODE39, CODE93, EAN13, EAN8, UPCA, UPCE, ITF, CODABAR;
  }

  /**
   * Encodes a barcode into the ZPL field commands (no {@code ^FO}, which the
   * renderer prepends), upright.
   *
   * @param symbology
   *        symbology name, e.g. {@code "code128"}, {@code "ean13"}, {@code "qr"}
   * @param data
   *        the barcode payload
   * @return the ZPL command string
   * @throws BarcodeException
   *         if the symbology is unknown or the data is invalid for it
   */
  public static String encode(String symbology, String data) {
    return encode(symbology, data, BarcodeOptions.DEFAULT);
  }

  /**
   * Encodes a barcode with explicit options, upright.
   *
   * @param symbology
   *        symbology name
   * @param data
   *        the barcode payload
   * @param options
   *        height, module size, HRI position and QR error correction; a module
   *        size of {@code 0} means the symbology's default
   * @return the ZPL command string
   * @throws BarcodeException
   *         if the symbology is unknown or the data is invalid for it
   */
  public static String encode(String symbology, String data, BarcodeOptions options) {
    return encode(symbology, data, options, Canvas.Direction.NORMAL);
  }

  /**
   * Encodes a barcode with explicit options at a field orientation (for a
   * rotated placement). QR ({@code ^BQ}) always prints upright and ignores the
   * orientation.
   *
   * @param symbology
   *        symbology name
   * @param data
   *        the barcode payload
   * @param options
   *        rendering options
   * @param orientation
   *        the field orientation for the barcode
   * @return the ZPL command string
   * @throws BarcodeException
   *         if the symbology is unknown or the data is invalid for it
   */
  public static String encode(String symbology, String data, BarcodeOptions options, Canvas.Direction orientation) {
    final String key = normalize(symbology);
    final int module = options.moduleSize();
    return switch (key) {
      case "qr", "qrcode" -> qr(data, module == 0 ? DEFAULT_QR_MAGNIFICATION : module, options.qrEc());
      case "pdf417" -> pdf417(data, module == 0 ? DEFAULT_PDF417_MODULE : module, orientation);
      default -> oneDimensional(symbologyOf(key), data, ZplCommands.orientation(orientation), options.heightDots(),
          module == 0 ? DEFAULT_MODULE : module, options.hri());
    };
  }

  /**
   * Whether a symbology renders as a 2D matrix (QR / PDF417) rather than 1D bars.
   *
   * @param symbology
   *        the symbology name, matched leniently (case and punctuation ignored)
   * @return {@code true} for {@code qr}/{@code pdf417}, {@code false} otherwise
   */
  public static boolean isTwoDimensional(String symbology) {
    return switch (normalize(symbology == null ? "" : symbology)) {
      case "qr", "qrcode", "pdf417" -> true;
      default -> false;
    };
  }

  private static String oneDimensional(Symbology symbology, String data, char o, int height, int module,
      BarcodeOptions.Hri hri) {
    validate(symbology, data);
    final char line = hri != BarcodeOptions.Hri.NONE ? 'Y' : 'N'; // print interpretation line
    final char above = hri == BarcodeOptions.Hri.ABOVE ? 'Y' : 'N'; // BOTH degrades to below
    final String command = switch (symbology) {
      case CODE128 -> "^BC" + o + "," + height + "," + line + "," + above + ",N,A";
      case CODE39 -> "^B3" + o + ",N," + height + "," + line + "," + above;
      case CODE93 -> "^BA" + o + "," + height + "," + line + "," + above + ",N";
      case EAN13 -> "^BE" + o + "," + height + "," + line + "," + above;
      case EAN8 -> "^B8" + o + "," + height + "," + line + "," + above;
      case UPCA -> "^BU" + o + "," + height + "," + line + "," + above + ",N";
      case UPCE -> "^B9" + o + "," + height + "," + line + "," + above + ",N";
      case ITF -> "^B2" + o + "," + height + "," + line + "," + above + ",N";
      case CODABAR -> "^BK" + o + ",N," + height + "," + line + "," + above + ",A,A";
    };
    return "^BY" + module + command + ZplCommands.fieldData(data);
  }

  private static String qr(String data, int magnification, BarcodeOptions.QrEc ec) {
    requireNonEmpty(data);
    // Corrected ^FD form: <error-correction level><input mode>, e.g. QA (automatic).
    return "^BQN,2," + magnification + "^FD" + ec.name().charAt(0) + "A," + data + "^FS";
  }

  private static String pdf417(String data, int module, Canvas.Direction orientation) {
    requireNonEmpty(data);
    return "^BY" + module + "^B7" + ZplCommands.orientation(orientation) + ZplCommands.fieldData(data);
  }

  private static void validate(Symbology symbology, String data) {
    requireNonEmpty(data);
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

  private static void requireNonEmpty(String data) {
    if (data == null || data.isEmpty()) {
      throw new BarcodeException("Barcode data must not be empty");
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
