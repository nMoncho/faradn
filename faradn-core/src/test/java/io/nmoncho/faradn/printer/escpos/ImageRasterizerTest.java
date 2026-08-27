package io.nmoncho.faradn.printer.escpos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.RasterImage;

public class ImageRasterizerTest {

  private static final int BLACK = 0xFF000000;
  private static final int WHITE = 0xFFFFFFFF;

  @Test
  void solidBlackImageRastersToAllSetBits() {
    byte[] out = ImageRasterizer.raster(solid(8, 8, BLACK), 512);

    byte[] header = { 0x1D, 0x76, 0x30, 0x00, 0x01, 0x00, 0x08, 0x00 }; // GS v 0, 1 byte/row, 8 rows
    assertArrayEquals(header, Arrays.copyOfRange(out, 0, header.length));
    assertEquals(header.length + 8, out.length);
    for (int i = header.length; i < out.length; i++) {
      assertEquals((byte) 0xFF, out[i]);
    }
  }

  @Test
  void solidWhiteImageRastersToNoSetBits() {
    byte[] out = ImageRasterizer.raster(solid(8, 8, WHITE), 512);

    assertEquals(8 + 8, out.length);
    for (int i = 8; i < out.length; i++) {
      assertEquals((byte) 0x00, out[i]);
    }
  }

  @Test
  void widerImageIsScaledToThePrintableWidth() {
    byte[] out = ImageRasterizer.raster(solid(100, 50, BLACK), 40);

    assertEquals(5, out[4] & 0xFF); // xL: ceil(40 / 8) bytes per row
    assertEquals(0, out[5] & 0xFF); // xH
    assertEquals(20, out[6] & 0xFF); // yL: round(50 * 40 / 100)
  }

  private static RasterImage solid(int width, int height, int argb) {
    int[] pixels = new int[width * height];
    Arrays.fill(pixels, argb);
    return new RasterImage(width, height, pixels);
  }
}
