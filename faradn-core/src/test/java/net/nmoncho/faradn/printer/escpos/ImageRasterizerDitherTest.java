//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.RasterImage;

/**
 * Exercises the Floyd-Steinberg error diffusion in {@link ImageRasterizer}
 * beyond the solid black / solid white cases: mid-tones, gradients, colour
 * luminance, transparency, determinism, and the intensity-preservation property
 * that makes dithering worth doing at all.
 */
class ImageRasterizerDitherTest {

  private static final int HEADER = 8; // GS v 0 + xL xH + yL yH

  @Test
  void midGrayProducesAMixOfBlackAndWhiteDots() {
    // A uniform 50% gray must not collapse to all-black or all-white; error
    // diffusion has to scatter dots. This is the case the old tests never hit.
    int width = 64;
    int height = 64;
    byte[] out = ImageRasterizer.raster(uniform(width, height, gray(128)), 512);

    long black = countBlackDots(out, width, height);
    long total = (long) width * height;
    assertTrue(black > 0 && black < total, "expected a mix of dots, got " + black + "/" + total);

    double fraction = black / (double) total;
    assertTrue(fraction > 0.4 && fraction < 0.6, "50% gray should dither to ~half black, was " + fraction);
  }

  @Test
  void ditheringApproximatelyPreservesAverageIntensity() {
    // The defining property of error diffusion: the fraction of black dots for a
    // uniform gray G tracks its darkness, (255 - G) / 255, within a small margin.
    int width = 80;
    int height = 50;
    long total = (long) width * height;

    for (int g : new int[] { 32, 64, 96, 128, 160, 192, 224 }) {
      byte[] out = ImageRasterizer.raster(uniform(width, height, gray(g)), 512);
      double fraction = countBlackDots(out, width, height) / (double) total;
      double expected = (255.0 - g) / 255.0;
      assertTrue(Math.abs(fraction - expected) < 0.08,
          "gray " + g + ": black fraction " + fraction + " should be near " + expected);
    }
  }

  @Test
  void horizontalGradientIsDarkerOnItsDarkSide() {
    // Left column is black, right column white; the dithered output must keep
    // more ink on the left half than the right half.
    int width = 64;
    int height = 16;
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int level = x * 255 / (width - 1); // 0 (black) .. 255 (white)
        pixels[y * width + x] = gray(level);
      }
    }
    byte[] out = ImageRasterizer.raster(new RasterImage(width, height, pixels), 512);

    long leftBlack = countBlackDots(out, width, height, 0, width / 2);
    long rightBlack = countBlackDots(out, width, height, width / 2, width);
    assertTrue(leftBlack > rightBlack, "dark side had " + leftBlack + " dots, light side " + rightBlack);
  }

  @Test
  void blackFractionFallsAsColourLuminanceRises() {
    // Luminance drives how much ink a solid colour leaves: dark colours dither to
    // mostly black, light colours to mostly white, monotonically in between.
    // (Only pure black and pure white have zero diffusion error and stay solid.)
    double blue = blackFraction(0xFF0000FF); // luminance ~29
    double red = blackFraction(0xFFFF0000); // ~76
    double green = blackFraction(0xFF00FF00); // ~149
    double yellow = blackFraction(0xFFFFFF00); // ~226

    assertTrue(blue > 0.75, "dark blue should be mostly black, was " + blue);
    assertTrue(yellow < 0.25, "yellow should be mostly white, was " + yellow);
    assertTrue(blue > red && red > green && green > yellow,
        "black fraction must fall as luminance rises: " + blue + " " + red + " " + green + " " + yellow);
  }

  @Test
  void fullyTransparentPixelsAreNotPrinted() {
    // Transparent black must be treated as white (no ink), never printed.
    byte[] out = ImageRasterizer.raster(uniform(16, 16, 0x00000000), 512);
    assertEquals(0, countBlackDots(out, 16, 16), "transparent pixels must not print");
  }

  @Test
  void ditheringIsDeterministic() {
    RasterImage image = uniform(40, 40, gray(100));
    byte[] first = ImageRasterizer.raster(image, 512);
    byte[] second = ImageRasterizer.raster(image, 512);
    assertTrue(Arrays.equals(first, second), "same input must produce identical bytes");
  }

  // --- helpers ---

  private static double blackFraction(int argb) {
    int width = 48;
    int height = 48;
    byte[] out = ImageRasterizer.raster(uniform(width, height, argb), 512);
    return countBlackDots(out, width, height) / (double) (width * height);
  }

  private static int gray(int level) {
    return 0xFF000000 | (level << 16) | (level << 8) | level;
  }

  private static RasterImage uniform(int width, int height, int argb) {
    int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return new RasterImage(width, height, pixels);
  }

  private static long countBlackDots(byte[] out, int width, int height) {
    return countBlackDots(out, width, height, 0, width);
  }

  /** Counts set bits in columns [fromX, toX) of the packed bitmap. */
  private static long countBlackDots(byte[] out, int width, int height, int fromX, int toX) {
    int bytesPerRow = (width + 7) / 8;
    long count = 0;
    for (int y = 0; y < height; y++) {
      for (int x = fromX; x < toX; x++) {
        int b = out[HEADER + y * bytesPerRow + (x / 8)] & 0xFF;
        if ((b & (0x80 >> (x % 8))) != 0) {
          count++;
        }
      }
    }
    return count;
  }
}
