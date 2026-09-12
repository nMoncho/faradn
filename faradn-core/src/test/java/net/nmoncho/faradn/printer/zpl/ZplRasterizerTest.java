//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.RasterImage;

class ZplRasterizerTest {

  private static final int BLACK = 0xFF000000;
  private static final int WHITE = 0xFFFFFFFF;

  @Test
  void solidBlackRowIsAllOneBits() {
    // 8 black dots -> one 0xFF byte; body reused verbatim (set bit = black), hex-encoded.
    assertEquals("^GFA,1,1,1,FF", ZplRasterizer.graphicField(solid(8, 1, BLACK), 8));
  }

  @Test
  void solidWhiteRowIsAllZeroBits() {
    assertEquals("^GFA,1,1,1,00", ZplRasterizer.graphicField(solid(8, 1, WHITE), 8));
  }

  @Test
  void widthDrivesBytesPerRow() {
    // 16 black dots -> 2 bytes per row.
    assertEquals("^GFA,2,2,2,FFFF", ZplRasterizer.graphicField(solid(16, 1, BLACK), 16));
  }

  private static RasterImage solid(int width, int height, int argb) {
    final int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return new RasterImage(width, height, pixels);
  }
}
