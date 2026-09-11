//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.RasterImage;
import net.nmoncho.faradn.printer.escpos.ImageRasterizer;
import net.nmoncho.faradn.printer.image.RasterBitmap;
import net.nmoncho.faradn.printer.image.RasterEncoder;

class StarRasterizerTest {

  private static RasterImage solid(int width, int height, int argb) {
    final int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return new RasterImage(width, height, pixels);
  }

  @Test
  void headerIsEscGsSWithNBetweenDimsAndData() {
    final RasterImage image = solid(16, 4, 0xFF000000); // solid black, 16 dots wide
    final byte[] out = StarRasterizer.raster(image, 512);
    final RasterBitmap expected = RasterEncoder.encode(image, 512);

    // ESC GS S m=1, xL xH (bytesPerRow), yL yH (height), n=00, then the packed body.
    final int bpr = expected.bytesPerRow(); // 16/8 = 2
    final int h = expected.height(); // 4
    final byte[] header = { 0x1B, 0x1D, 0x53, 0x01, (byte) (bpr & 0xFF), (byte) (bpr >> 8),
        (byte) (h & 0xFF), (byte) (h >> 8), 0x00 };
    assertArrayEquals(header, Arrays.copyOfRange(out, 0, header.length));
    assertArrayEquals(expected.body(), Arrays.copyOfRange(out, header.length, out.length));
  }

  @Test
  void bodyIsByteIdenticalToTheEscPosRaster() {
    // The packed bits are shared; only the header differs (9-byte ESC GS S vs
    // 8-byte GS v 0), so the bodies must match byte for byte.
    final RasterImage image = solid(24, 6, 0xFF000000);
    final byte[] star = StarRasterizer.raster(image, 512);
    final byte[] escpos = ImageRasterizer.raster(image, 512);

    assertArrayEquals(Arrays.copyOfRange(escpos, 8, escpos.length), Arrays.copyOfRange(star, 9, star.length));
  }
}
