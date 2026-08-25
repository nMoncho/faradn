package io.nmoncho.faradn.printer.escpos;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Rasterizes an image into an ESC/POS raster bit image ({@code GS v 0}): it
 * scales the image down to the printable width, converts it to 1-bit using
 * Floyd–Steinberg error diffusion, and packs the pixels MSB-first, eight
 * horizontal dots per byte (a set bit prints as black).
 * <p>
 * This is the JVM path and uses {@code java.awt}. The native binary substitutes
 * a pure-Java decoder (see the plan), but the raster packing here is shared.
 */
public final class ImageRasterizer {

  private static final int THRESHOLD = 128;

  private ImageRasterizer() {
  }

  /**
   * Rasterizes {@code image}, scaling it to at most {@code maxWidthDots} wide.
   *
   * @param image
   *        the source image
   * @param maxWidthDots
   *        the printer's printable width in dots
   * @return the {@code GS v 0} command with its packed bitmap
   */
  public static byte[] raster(BufferedImage image, int maxWidthDots) {
    final BufferedImage scaled = scaleToWidth(image, maxWidthDots);
    final int width = scaled.getWidth();
    final int height = scaled.getHeight();

    // Grayscale, then Floyd–Steinberg dithering into a black/white mask.
    final int[] gray = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        gray[y * width + x] = luminance(scaled.getRGB(x, y));
      }
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
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] { Code.GS, 0x76, 0x30, 0x00 }); // GS v 0, mode 0
    out.writeBytes(new byte[] { (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF) }); // xL xH
    out.writeBytes(new byte[] { (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF) }); // yL yH
    for (int y = 0; y < height; y++) {
      for (int bx = 0; bx < bytesPerRow; bx++) {
        int b = 0;
        for (int bit = 0; bit < 8; bit++) {
          final int x = bx * 8 + bit;
          if (x < width && black[y * width + x]) {
            b |= 0x80 >> bit;
          }
        }
        out.write(b);
      }
    }
    return out.toByteArray();
  }

  private static BufferedImage scaleToWidth(BufferedImage src, int maxWidth) {
    if (src.getWidth() <= maxWidth) {
      return src;
    }
    final int newWidth = maxWidth;
    final int newHeight = Math.max(1, (int) Math.round(src.getHeight() * (maxWidth / (double) src.getWidth())));
    final BufferedImage dst = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = dst.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(src, 0, 0, newWidth, newHeight, null);
    g.dispose();
    return dst;
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
