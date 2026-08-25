package io.nmoncho.faradn.printer.escpos.commands;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import io.nmoncho.faradn.BarcodeException;
import io.nmoncho.faradn.printer.escpos.Code;

/**
 * Builds ESC/POS barcode byte sequences: 1D symbologies via {@code GS k}
 * (function B, length-prefixed) framed with sensible HRI, height and module
 * settings; and 2D via {@code GS ( k} (QR Code and PDF417).
 */
public final class BarcodeCommands {

  private static final int GS = Code.GS;

  private static final int DEFAULT_HEIGHT_DOTS = 100;
  private static final int DEFAULT_MODULE_WIDTH = 3;
  private static final int HRI_BELOW = 2;
  private static final int HRI_FONT_A = 0;
  private static final int DEFAULT_QR_MODULE = 6;
  private static final int DEFAULT_PDF417_MODULE = 3;

  private BarcodeCommands() {
  }

  private enum Symbology {
    UPCA(65), UPCE(66), EAN13(67), EAN8(68), CODE39(69), ITF(70), CODABAR(71), CODE93(72), CODE128(73);

    private final int code;

    Symbology(int code) {
      this.code = code;
    }
  }

  /**
   * Encodes a barcode into the full ESC/POS command sequence (settings, data and
   * print), not including alignment (which the renderer applies).
   *
   * @param symbology
   *        symbology name, e.g. {@code "code128"}, {@code "ean13"}, {@code "qr"}
   * @param data
   *        the barcode payload
   * @return the ESC/POS bytes
   * @throws BarcodeException
   *         if the symbology is unknown or the data is invalid for it
   */
  public static byte[] encode(String symbology, String data) {
    final String key = normalize(symbology);
    return switch (key) {
      case "qr", "qrcode" -> qr(data, DEFAULT_QR_MODULE);
      case "pdf417" -> pdf417(data, DEFAULT_PDF417_MODULE);
      default -> oneDimensional(symbologyOf(key), data);
    };
  }

  private static byte[] oneDimensional(Symbology symbology, String data) {
    validate(symbology, data);

    final String payloadText = symbology == Symbology.CODE128 && !data.startsWith("{") ? "{B" + data : data;
    final byte[] payload = payloadText.getBytes(StandardCharsets.US_ASCII);
    if (payload.length > 255) {
      throw new BarcodeException("Barcode data is too long: " + payload.length + " bytes");
    }

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] { (byte) GS, 0x48, (byte) HRI_BELOW }); // GS H: HRI below
    out.writeBytes(new byte[] { (byte) GS, 0x66, (byte) HRI_FONT_A }); // GS f: HRI font A
    out.writeBytes(new byte[] { (byte) GS, 0x68, (byte) DEFAULT_HEIGHT_DOTS }); // GS h: height
    out.writeBytes(new byte[] { (byte) GS, 0x77, (byte) DEFAULT_MODULE_WIDTH }); // GS w: module width
    out.writeBytes(new byte[] { (byte) GS, 0x6B, (byte) symbology.code, (byte) payload.length }); // GS k m n
    out.writeBytes(payload);
    return out.toByteArray();
  }

  private static byte[] qr(String data, int moduleSize) {
    final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    gsParenK(out, 49, 65, new byte[] { 50, 0 }); // select model 2
    gsParenK(out, 49, 67, new byte[] { (byte) moduleSize }); // module size
    gsParenK(out, 49, 69, new byte[] { 49 }); // error correction level M
    final byte[] store = new byte[bytes.length + 1];
    store[0] = 48; // store function
    System.arraycopy(bytes, 0, store, 1, bytes.length);
    gsParenK(out, 49, 80, store); // store the data
    gsParenK(out, 49, 81, new byte[] { 48 }); // print
    return out.toByteArray();
  }

  private static byte[] pdf417(String data, int moduleWidth) {
    final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    gsParenK(out, 48, 67, new byte[] { (byte) moduleWidth }); // module width
    gsParenK(out, 48, 68, new byte[] { 3 }); // row height
    final byte[] store = new byte[bytes.length + 1];
    store[0] = 48; // store function
    System.arraycopy(bytes, 0, store, 1, bytes.length);
    gsParenK(out, 48, 80, store); // store the data
    gsParenK(out, 48, 81, new byte[] { 48 }); // print
    return out.toByteArray();
  }

  /** Emits {@code GS ( k pL pH cn fn params...}, computing the length prefix. */
  private static void gsParenK(ByteArrayOutputStream out, int cn, int fn, byte[] params) {
    final int len = 2 + params.length; // cn + fn + params
    out.writeBytes(new byte[] { (byte) GS, 0x28, 0x6B, (byte) (len & 0xFF), (byte) ((len >> 8) & 0xFF),
        (byte) cn, (byte) fn });
    out.writeBytes(params);
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
