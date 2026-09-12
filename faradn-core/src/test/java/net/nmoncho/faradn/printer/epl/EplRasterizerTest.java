//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.RasterImage;

class EplRasterizerTest {

  private static final int BLACK = 0xFF000000;
  private static final int WHITE = 0xFFFFFFFF;

  @Test
  void solidBlackRowInvertsToZeroBits() {
    // RasterBitmap has 1 = black (0xFF); EPL GW wants 0 = black, so the body inverts to 0x00.
    assertArrayEquals(gw("GW0,0,1,1", new byte[] { 0x00 }), EplRasterizer.graphicWrite(solid(8, 1, BLACK), 0, 0, 8));
  }

  @Test
  void solidWhiteRowInvertsToOneBits() {
    assertArrayEquals(gw("GW0,0,1,1", new byte[] { (byte) 0xFF }),
        EplRasterizer.graphicWrite(solid(8, 1, WHITE), 0, 0, 8));
  }

  @Test
  void widthDrivesBytesPerRowAndOriginIsInHeader() {
    assertArrayEquals(gw("GW5,7,2,1", new byte[] { 0x00, 0x00 }),
        EplRasterizer.graphicWrite(solid(16, 1, BLACK), 5, 7, 16));
  }

  private static byte[] gw(String header, byte[] body) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(body);
    return out.toByteArray();
  }

  private static RasterImage solid(int width, int height, int argb) {
    final int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return new RasterImage(width, height, pixels);
  }
}
