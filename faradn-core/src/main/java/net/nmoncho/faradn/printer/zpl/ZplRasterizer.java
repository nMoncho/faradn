//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import net.nmoncho.faradn.RasterImage;
import net.nmoncho.faradn.printer.image.RasterBitmap;
import net.nmoncho.faradn.printer.image.RasterEncoder;
import net.nmoncho.faradn.printer.label.HexEncoder;

/**
 * Wraps the shared {@link RasterEncoder} output as a ZPL {@code ^GFA} graphic
 * field. The packed rows ({@link RasterBitmap#body()}) are reused verbatim: ZPL
 * and the raster encoder share the same MSB-first, set-bit-black convention, so
 * the body is only hex-encoded (via {@link HexEncoder}), not reordered or
 * inverted. This is the label counterpart to the ESC/POS
 * {@code ImageRasterizer}
 * and the StarPRNT {@code StarRasterizer}.
 * <p>
 * {@code ^GFA} (ASCII hex) is preferred over {@code ^GFB} (raw binary) for
 * transport safety, since raw graphic bytes can collide with the {@code ^}/
 * {@code ~} command prefixes. Format {@code ^GFA,b,c,d,data} (guide "^GF"
 * p160): {@code b} = byte count, {@code c} = graphic field count (both equal
 * the
 * body length for an uncompressed image), {@code d} = bytes per row.
 * <p>
 * <strong>Not part of the public API.</strong>
 */
public final class ZplRasterizer {

  private ZplRasterizer() {
  }

  /**
   * Encodes an image as a {@code ^GFA} graphic field, scaled to at most
   * {@code maxWidthDots}.
   *
   * @param image
   *        the source image
   * @param maxWidthDots
   *        the maximum width in dots (the label's printable width)
   * @return the {@code ^GFA} command string (no {@code ^FO}, which the renderer
   *         prepends)
   */
  public static String graphicField(RasterImage image, int maxWidthDots) {
    final RasterBitmap bitmap = RasterEncoder.encode(image, maxWidthDots);
    final int byteCount = bitmap.body().length;
    return "^GFA," + byteCount + "," + byteCount + "," + bitmap.bytesPerRow() + "," + HexEncoder.toHex(bitmap.body());
  }
}
