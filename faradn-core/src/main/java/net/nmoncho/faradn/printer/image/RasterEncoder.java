//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.image;

import net.nmoncho.faradn.RasterImage;

/**
 * Turns an image into a packed 1-bit {@link RasterBitmap}: it scales the image
 * down to the printable width, converts it to black/white using Floyd–Steinberg
 * error diffusion, and packs the pixels MSB-first, eight horizontal dots per
 * byte
 * (a set bit prints as black).
 * <p>
 * This is the language-neutral core shared by every raster backend; the
 * protocol
 * header (ESC/POS {@code GS v 0}, StarPRNT {@code ESC GS S}) is prepended by
 * the
 * caller. It works on {@link RasterImage} pixels, not {@code java.awt}, so it
 * runs
 * unchanged in the native binary.
 */
public final class RasterEncoder {

  private static final int THRESHOLD = 128;

  private RasterEncoder() {
  }

  /**
   * Encodes {@code image}, scaling it to at most {@code maxWidthDots} wide.
   *
   * @param image
   *        the source pixels
   * @param maxWidthDots
   *        the printer's printable width in dots
   * @return the scaled, dithered, packed bitmap (without any protocol header)
   */
  public static RasterBitmap encode(RasterImage image, int maxWidthDots) {
    final RasterImage scaled = scaleToWidth(image, maxWidthDots);
    final int width = scaled.width();
    final int height = scaled.height();
    final int[] pixels = scaled.argb();

    // Grayscale, then Floyd–Steinberg dithering into a black/white mask.
    final int[] gray = new int[width * height];
    for (int i = 0; i < gray.length; i++) {
      gray[i] = luminance(pixels[i]);
    }

    final boolean[] black = new boolean[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        final int idx = y * width + x;
        final int old = clamp(gray[idx]);
        final boolean isBlack = old < THRESHOLD;
        black[idx] = isBlack;
        final int error = old - (isBlack ? 0 : 255);
        if (x + 1 < width) {
          gray[idx + 1] += error * 7 / 16;
        }
        if (y + 1 < height) {
          if (x > 0) {
            gray[idx + width - 1] += error * 3 / 16;
          }
          gray[idx + width] += error * 5 / 16;
          if (x + 1 < width) {
            gray[idx + width + 1] += error / 16;
          }
        }
      }
    }

    final int bytesPerRow = (width + 7) / 8;
    final byte[] body = new byte[height * bytesPerRow];
    int pos = 0;
    for (int y = 0; y < height; y++) {
      for (int bx = 0; bx < bytesPerRow; bx++) {
        int b = 0;
        for (int bit = 0; bit < 8; bit++) {
          final int x = bx * 8 + bit;
          if (x < width && black[y * width + x]) {
            b |= 0x80 >> bit;
          }
        }
        body[pos++] = (byte) b;
      }
    }
    return new RasterBitmap(width, height, bytesPerRow, body);
  }

  private static RasterImage scaleToWidth(RasterImage src, int maxWidth) {
    if (src.width() <= maxWidth) {
      return src;
    }
    final int newWidth = maxWidth;
    final int newHeight = Math.max(1, (int) Math.round(src.height() * (maxWidth / (double) src.width())));
    final int[] out = new int[newWidth * newHeight];
    for (int y = 0; y < newHeight; y++) {
      final int sourceY = y * src.height() / newHeight;
      for (int x = 0; x < newWidth; x++) {
        final int sourceX = x * src.width() / newWidth;
        out[y * newWidth + x] = src.argb()[sourceY * src.width() + sourceX];
      }
    }
    return new RasterImage(newWidth, newHeight, out);
  }

  private static int luminance(int argb) {
    final int alpha = (argb >>> 24) & 0xFF;
    if (alpha < THRESHOLD) {
      return 255; // treat transparent pixels as white (do not print)
    }
    final int r = (argb >> 16) & 0xFF;
    final int g = (argb >> 8) & 0xFF;
    final int b = argb & 0xFF;
    return (int) (0.299 * r + 0.587 * g + 0.114 * b);
  }

  private static int clamp(int value) {
    return value < 0 ? 0 : Math.min(value, 255);
  }
}
