//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.label;

/**
 * Encodes bytes as ASCII hexadecimal (two uppercase digits per byte), the form
 * ZPL's {@code ^GFA} graphic field expects for a monochrome bitmap. The packed
 * rows from {@code RasterEncoder} ({@code RasterBitmap.body}) are hex-encoded
 * with no bit reordering, because ZPL and the raster encoder share the same
 * MSB-first, set-bit-black convention.
 * <p>
 * <strong>Not part of the public API.</strong>
 */
public final class HexEncoder {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private HexEncoder() {
  }

  /**
   * Encodes bytes as uppercase ASCII hex, two digits per byte.
   *
   * @param data
   *        the bytes to encode
   * @return the hex string ({@code data.length * 2} characters)
   */
  public static String toHex(byte[] data) {
    if (data == null) {
      throw new IllegalArgumentException("data must not be null");
    }
    final StringBuilder hex = new StringBuilder(data.length * 2);
    for (byte b : data) {
      hex.append(HEX[(b >> 4) & 0x0F]);
      hex.append(HEX[b & 0x0F]);
    }
    return hex.toString();
  }
}
