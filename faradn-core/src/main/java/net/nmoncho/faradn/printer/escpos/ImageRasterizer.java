//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos;

import java.io.ByteArrayOutputStream;

import net.nmoncho.faradn.RasterImage;
import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.image.RasterBitmap;
import net.nmoncho.faradn.printer.image.RasterEncoder;

/**
 * Wraps a packed 1-bit bitmap (from the shared {@link RasterEncoder}) in an
 * ESC/POS raster bit image command ({@code GS v 0}, mode 0): the header carries
 * the row width in bytes ({@code xL xH}) and the height in rows
 * ({@code yL yH}),
 * little-endian, followed by the packed body.
 */
public final class ImageRasterizer {

  private ImageRasterizer() {
  }

  /**
   * Rasterizes {@code image}, scaling it to at most {@code maxWidthDots} wide.
   *
   * @param image
   *        the source pixels
   * @param maxWidthDots
   *        the printer's printable width in dots
   * @return the {@code GS v 0} command with its packed bitmap
   */
  public static byte[] raster(RasterImage image, int maxWidthDots) {
    final RasterBitmap bitmap = RasterEncoder.encode(image, maxWidthDots);
    final int bytesPerRow = bitmap.bytesPerRow();
    final int height = bitmap.height();

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] { Code.GS, 0x76, 0x30, 0x00 }); // GS v 0, mode 0
    out.writeBytes(new byte[] { (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF) }); // xL xH
    out.writeBytes(new byte[] { (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF) }); // yL yH
    out.writeBytes(bitmap.body());
    return out.toByteArray();
  }
}
