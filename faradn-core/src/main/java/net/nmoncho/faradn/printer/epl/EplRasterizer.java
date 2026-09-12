//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import net.nmoncho.faradn.RasterImage;
import net.nmoncho.faradn.printer.image.RasterBitmap;
import net.nmoncho.faradn.printer.image.RasterEncoder;

/**
 * Wraps the shared {@link RasterEncoder} output as an EPL2 {@code GW} (direct
 * graphic write) command. The command is {@code GW x,y,widthBytes,heightDots}
 * followed by {@code widthBytes * heightDots} raw bytes.
 * <p>
 * <strong>Bit polarity is inverted relative to ZPL.</strong> EPL2 {@code GW}
 * treats a {@code 0} bit as a printed (black) dot and a {@code 1} bit as white,
 * the opposite of the shared {@link RasterBitmap} (set bit = black). So the
 * packed body is emitted bit-inverted (bitwise NOT); the byte and row layout
 * (MSB-first, top-to-bottom) are reused unchanged. Padding bits past the image
 * width were white in the bitmap and stay white after inversion. Confirm the
 * polarity on real hardware (a black/white swap is silent until printed).
 * <p>
 * Because the body is binary, this returns {@code byte[]} (not a String): the
 * renderer writes these bytes directly, not through the text code-page encoder.
 * <p>
 * <strong>Not part of the public API.</strong>
 */
public final class EplRasterizer {

  private EplRasterizer() {
  }

  /**
   * Encodes an image as a {@code GW} command at {@code (x, y)}, scaled to at most
   * {@code maxWidthDots}.
   *
   * @param image
   *        the source image
   * @param xDots
   *        field origin x
   * @param yDots
   *        field origin y
   * @param maxWidthDots
   *        the maximum width in dots
   * @return the {@code GW} header bytes followed by the bit-inverted body
   */
  public static byte[] graphicWrite(RasterImage image, int xDots, int yDots, int maxWidthDots) {
    final RasterBitmap bitmap = RasterEncoder.encode(image, maxWidthDots);
    final byte[] body = bitmap.body();
    final byte[] inverted = new byte[body.length];
    for (int i = 0; i < body.length; i++) {
      inverted[i] = (byte) ~body[i]; // EPL GW: 0 = black, 1 = white
    }
    final String header = "GW" + xDots + "," + yDots + "," + bitmap.bytesPerRow() + "," + bitmap.height();
    final ByteArrayOutputStream out = new ByteArrayOutputStream(header.length() + inverted.length);
    out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(inverted);
    return out.toByteArray();
  }
}
