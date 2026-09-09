package net.nmoncho.faradn;

/**
 * Decoded image pixels: ARGB, row-major, one {@code int} per pixel. This is the
 * java.awt-free representation the renderer rasterizes, so images work in the
 * native binary (where {@code java.awt}/ImageIO's native library is
 * unavailable).
 */
public record RasterImage(int width, int height, int[] argb) {

  /** The largest image side (in pixels) the decoders will allocate. */
  static final int MAX_SIDE = 4096;

  /**
   * The largest pixel count the decoders will allocate ({@link #MAX_SIDE}
   * squared).
   */
  static final long MAX_PIXELS = (long) MAX_SIDE * MAX_SIDE;

  public RasterImage {
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("image must be at least 1x1, got " + width + "x" + height);
    }
    if (argb == null || argb.length != width * height) {
      throw new IllegalArgumentException("argb length must be width*height");
    }
  }

  /**
   * Rejects image dimensions before a {@code width * height} buffer is allocated,
   * so an attacker-controlled size (a decompression or dimension bomb) cannot
   * exhaust memory. Callers must invoke this <em>before</em> allocating pixels.
   *
   * @param width
   *        candidate width in pixels
   * @param height
   *        candidate height in pixels
   * @throws PrintingException
   *         if the dimensions are non-positive or exceed {@link #MAX_SIDE} per
   *         side or {@link #MAX_PIXELS} in total
   */
  static void checkDimensions(int width, int height) {
    if (width < 1 || height < 1) {
      throw new PrintingException("image must be at least 1x1, got " + width + "x" + height);
    }
    if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
      throw new PrintingException(
          "image dimensions " + width + "x" + height + " exceed the maximum of " + MAX_SIDE + "x" + MAX_SIDE);
    }
  }
}
