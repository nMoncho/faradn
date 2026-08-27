package io.nmoncho.faradn;

/**
 * Decoded image pixels: ARGB, row-major, one {@code int} per pixel. This is the
 * java.awt-free representation the renderer rasterizes, so images work in the
 * native binary (where {@code java.awt}/ImageIO's native library is
 * unavailable).
 */
public record RasterImage(int width, int height, int[] argb) {

  public RasterImage {
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("image must be at least 1x1, got " + width + "x" + height);
    }
    if (argb == null || argb.length != width * height) {
      throw new IllegalArgumentException("argb length must be width*height");
    }
  }
}
