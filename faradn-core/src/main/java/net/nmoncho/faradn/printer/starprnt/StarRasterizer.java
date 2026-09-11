//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt;

import java.io.ByteArrayOutputStream;

import net.nmoncho.faradn.RasterImage;
import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.image.RasterBitmap;
import net.nmoncho.faradn.printer.image.RasterEncoder;

/**
 * Wraps a packed 1-bit bitmap (from the shared {@link RasterEncoder}) in a
 * StarPRNT raster command, {@code ESC GS S 01 xL xH yL yH 00 <data>}. The
 * packed
 * body is byte-identical to the ESC/POS {@code GS v 0} output; only the header
 * differs - {@code ESC GS S 01} instead of {@code GS v 0 0}, plus a single
 * {@code n=00} between {@code yH} and the first data byte. Verified against the
 * StarPRNT Command Specifications (Rev 4.20), pp64-65 (row width &le; 128
 * bytes;
 * scaling to the printable width keeps it in range).
 */
public final class StarRasterizer {

  private StarRasterizer() {
  }

  /**
   * Rasterizes {@code image}, scaling it to at most {@code maxWidthDots} wide.
   *
   * @param image
   *        the source pixels
   * @param maxWidthDots
   *        the printer's printable width in dots
   * @return the {@code ESC GS S} command with its packed bitmap
   */
  public static byte[] raster(RasterImage image, int maxWidthDots) {
    final RasterBitmap bitmap = RasterEncoder.encode(image, maxWidthDots);
    final int bytesPerRow = bitmap.bytesPerRow();
    final int height = bitmap.height();

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] { Code.ESC, Code.GS, 0x53, 0x01, // ESC GS S m=1
        (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF), // xL xH
        (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF), // yL yH
        0x00 }); // n
    out.writeBytes(bitmap.body());
    return out.toByteArray();
  }
}
