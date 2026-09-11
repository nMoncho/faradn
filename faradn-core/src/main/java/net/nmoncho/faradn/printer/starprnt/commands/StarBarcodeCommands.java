//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt.commands;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import net.nmoncho.faradn.BarcodeException;
import net.nmoncho.faradn.document.BarcodeOptions;
import net.nmoncho.faradn.printer.command.Code;

/**
 * Builds StarPRNT barcode byte sequences for the TSP143IV, mirroring the
 * neutral
 * {@code encode(symbology, data, options)} surface of the ESC/POS
 * {@code BarcodeCommands} so the renderer's call site is identical:
 * <ul>
 * <li><b>1D</b> is one combined command {@code ESC b n1 n2 n3 n4 <data> RS}
 * (not
 * ESC/POS's four settings + a print). The payload is {@code RS}-terminated, so
 * it must not contain {@code 0x1E}. Code128 needs no {@code {B} prefix.</li>
 * <li><b>QR</b> is {@code ESC GS y} (set model/EC/cell, auto-store,
 * print).</li>
 * <li><b>PDF417</b> is {@code ESC GS x} (set size/ECC/module/aspect, store,
 * print).</li>
 * </ul>
 * Verified against the StarPRNT Command Specifications (Rev 4.20), pp81-94 and
 * the bar-code appendix pp208-216. HRI {@code ABOVE}/{@code BOTH} have no Star
 * expression and degrade to {@code BELOW}.
 */
public final class StarBarcodeCommands {

  private static final byte ESC = Code.ESC;
  private static final byte GS = Code.GS;
  private static final byte RS = 0x1E; // 1D data terminator

  private static final int DEFAULT_1D_WIDTH_MODE = 2; // ESC b n3: ~3-dot module
  private static final int QR_MODEL = 2;
  private static final int DEFAULT_QR_CELL = 3;
  private static final int DEFAULT_PDF417_MODULE = 2;
  private static final int DEFAULT_PDF417_ECC = 1;
  private static final int DEFAULT_PDF417_ASPECT = 3;

  private StarBarcodeCommands() {
  }

  private enum Symbology {
    UPCE(0), UPCA(1), EAN8(2), EAN13(3), CODE39(4), ITF(5), CODE128(6), CODE93(7), CODABAR(8);

    private final int code;

    Symbology(int code) {
      this.code = code;
    }
  }

  /**
   * Encodes a barcode into the full StarPRNT command sequence (settings, data and
   * print), not including alignment (which the renderer applies).
   *
   * @param symbology
   *        symbology name, e.g. {@code "code128"}, {@code "ean13"}, {@code "qr"}
   * @param data
   *        the barcode payload
   * @return the StarPRNT bytes
   * @throws BarcodeException
   *         if the symbology is unknown or the data is invalid for it
   */
  public static byte[] encode(String symbology, String data) {
    return encode(symbology, data, BarcodeOptions.DEFAULT);
  }

  /**
   * Encodes a barcode with explicit rendering options.
   *
   * @param symbology
   *        symbology name, e.g. {@code "code128"}, {@code "ean13"}, {@code "qr"}
   * @param data
   *        the barcode payload
   * @param options
   *        height, module size, HRI position and QR error correction; a module
   *        size of {@code 0} means the symbology's default
   * @return the StarPRNT bytes
   * @throws BarcodeException
   *         if the symbology is unknown or the data is invalid for it
   */
  public static byte[] encode(String symbology, String data, BarcodeOptions options) {
    final String key = normalize(symbology);
    final int module = options.moduleSize();
    return switch (key) {
      case "qr", "qrcode" -> qr(data, module == 0 ? DEFAULT_QR_CELL : module, options.qrEc().ordinal());
      case "pdf417" -> pdf417(data, module == 0 ? DEFAULT_PDF417_MODULE : module);
      default ->
        oneDimensional(symbologyOf(key), data, options.heightDots(), widthMode(module), hriFlag(options.hri()));
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

  private static byte[] oneDimensional(Symbology symbology, String data, int heightDots, int widthMode, int hriFlag) {
    validate(symbology, data);

    final byte[] payload = data.getBytes(StandardCharsets.US_ASCII);
    for (byte b : payload) {
      if (b == RS) {
        throw new BarcodeException("Star 1D barcode data must not contain the RS terminator (0x1E)");
      }
    }

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    // ESC b n1 n2 n3 n4 : symbology, HRI+LF flag, width mode, height
    out.writeBytes(
        new byte[] { ESC, 0x62, (byte) symbology.code, (byte) hriFlag, (byte) widthMode, (byte) heightDots });
    out.writeBytes(payload);
    out.write(RS);
    return out.toByteArray();
  }

  private static byte[] qr(String data, int cellSize, int ecLevel) {
    if (data.isEmpty()) {
      throw new BarcodeException("Barcode data must not be empty");
    }
    final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] { ESC, GS, 0x79, 0x53, 0x30, (byte) QR_MODEL }); // ESC GS y S 0 : model
    out.writeBytes(new byte[] { ESC, GS, 0x79, 0x53, 0x31, (byte) ecLevel }); // ESC GS y S 1 : EC level
    out.writeBytes(new byte[] { ESC, GS, 0x79, 0x53, 0x32, (byte) cellSize }); // ESC GS y S 2 : cell size
    out.writeBytes(new byte[] { ESC, GS, 0x79, 0x44, 0x31, 0x00, // ESC GS y D 1 m=0 : auto data-store
        (byte) (bytes.length & 0xFF), (byte) ((bytes.length >> 8) & 0xFF) });
    out.writeBytes(bytes);
    out.writeBytes(new byte[] { ESC, GS, 0x79, 0x50 }); // ESC GS y P : print
    return out.toByteArray();
  }

  private static byte[] pdf417(String data, int moduleWidth) {
    if (data.isEmpty()) {
      throw new BarcodeException("Barcode data must not be empty");
    }
    final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] { ESC, GS, 0x78, 0x53, 0x30, 0x00, 0x00, 0x00 }); // ESC GS x S 0 : size (auto)
    out.writeBytes(new byte[] { ESC, GS, 0x78, 0x53, 0x31, (byte) DEFAULT_PDF417_ECC }); // ESC GS x S 1 : ECC
    out.writeBytes(new byte[] { ESC, GS, 0x78, 0x53, 0x32, (byte) moduleWidth }); // ESC GS x S 2 : X module
    out.writeBytes(new byte[] { ESC, GS, 0x78, 0x53, 0x33, (byte) DEFAULT_PDF417_ASPECT }); // ESC GS x S 3 : aspect
    out.writeBytes(new byte[] { ESC, GS, 0x78, 0x44, // ESC GS x D : data-store (no m header)
        (byte) (bytes.length & 0xFF), (byte) ((bytes.length >> 8) & 0xFF) });
    out.writeBytes(bytes);
    out.writeBytes(new byte[] { ESC, GS, 0x78, 0x50 }); // ESC GS x P : print
    return out.toByteArray();
  }

  /**
   * Maps an ESC/POS-style module dot count to a Star {@code ESC b} width mode
   * (1..3).
   */
  private static int widthMode(int module) {
    if (module <= 0) {
      return DEFAULT_1D_WIDTH_MODE;
    }
    return Math.min(3, Math.max(1, module - 1));
  }

  /**
   * Maps an HRI position to the {@code ESC b n2} flag, always with a trailing
   * line feed: {@code 1} = no HRI, {@code 2} = HRI below. Star has no
   * above/both for standard 1D codes, so those degrade to below.
   */
  private static int hriFlag(BarcodeOptions.Hri hri) {
    return hri == BarcodeOptions.Hri.NONE ? 1 : 2;
  }

  private static void validate(Symbology symbology, String data) {
    if (data.isEmpty()) {
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
