package net.nmoncho.faradn;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.jsoup.nodes.Element;

/**
 * Image inside a {@link Document}, decoded to {@link
 * RasterImage} pixels.
 * <p>
 * PNGs are decoded in pure Java ({@link PngDecoder}) so they work in the native
 * binary; other formats (JPEG, BMP, WBMP) fall back to {@code javax.imageio},
 * which is available on the JVM but not inside the GraalVM native image.
 */
public class Image {

  private static final Pattern BASE64_REGEX = Pattern.compile("^data:image\\/(.+?);base64,(.+?)$");

  private final Supplier<RasterImage> source;
  private final Optional<Integer> height;
  private final Optional<Integer> width;

  private RasterImage raster;

  private Image(Supplier<RasterImage> source, Optional<Integer> height, Optional<Integer> width) {
    this.source = source;
    this.height = height;
    this.width = width;
  }

  /**
   * Decodes the image to ARGB pixels, applying the element's width/height when
   * they were set.
   *
   * @return the decoded pixels
   */
  public RasterImage raster() {
    synchronized (this) {
      if (raster == null) {
        final RasterImage decoded = source.get();
        final int targetWidth = width.orElse(decoded.width());
        final int targetHeight = height.orElse(decoded.height());
        raster = scale(decoded, targetWidth, targetHeight);
      }
    }
    return raster;
  }

  /**
   * Creates an image element from a URL.
   *
   * @param url
   *        where to get the image from
   * @return image element
   */
  public static Image fromUrl(String url) {
    return new Image(urlLoader(url), Optional.empty(), Optional.empty());
  }

  /**
   * Wraps already-decoded pixels.
   *
   * @param image
   *        the decoded pixels
   * @return image element
   */
  public static Image of(RasterImage image) {
    return new Image(() -> image, Optional.empty(), Optional.empty());
  }

  /**
   * Creates an image element from a URL, scaling to {@code height} and
   * {@code width}.
   *
   * @param url
   *        where to get the image from
   * @param height
   *        target height
   * @param width
   *        target width
   * @return image element
   */
  public static Image fromUrl(String url, Integer height, Integer width) {
    return new Image(urlLoader(url), Optional.of(height), Optional.of(width));
  }

  /**
   * Creates an image element from a Base64 encoding.
   *
   * @param base64
   *        Base64-encoded image
   * @return image element
   */
  public static Image fromBase64(String base64) {
    return new Image(base64Loader(base64), Optional.empty(), Optional.empty());
  }

  /**
   * Creates an image element from a Base64 encoding, scaling to {@code height}
   * and {@code width}.
   *
   * @param base64
   *        Base64-encoded image
   * @param height
   *        target height
   * @param width
   *        target width
   * @return image element
   */
  public static Image fromBase64(String base64, Integer height, Integer width) {
    return new Image(base64Loader(base64), Optional.of(height), Optional.of(width));
  }

  /**
   * Creates an image element from an {@link Element}, handling either a URL or a
   * Base64 {@code data:} URI.
   *
   * @param el
   *        HTML element
   * @return image element
   */
  public static Image fromNode(Element el) {
    if (el.tag().getName().equals("img") && !el.attr("src").trim().isEmpty()) {
      final String src = el.absUrl("src");
      final Optional<Integer> height = Utils.parseAttribute(el, "height");
      final Optional<Integer> width = Utils.parseAttribute(el, "width");

      final Matcher matcher = BASE64_REGEX.matcher(src);
      if (matcher.matches()) {
        return new Image(base64Loader(matcher.group(2)), height, width);
      }
      return new Image(urlLoader(src), height, width);
    }
    throw new PrintingException("Element [" + el + "] must be a <img /> tag, and have a valid `src` attribute");
  }

  private static Supplier<RasterImage> base64Loader(String base64) {
    return () -> decode(Base64.getDecoder().decode(base64));
  }

  private static Supplier<RasterImage> urlLoader(String url) {
    return () -> {
      try (InputStream in = new URL(url).openStream()) {
        return decode(in.readAllBytes());
      } catch (IOException ex) {
        throw new PrintingException("Failed to read image from url [" + url + "]", ex);
      }
    };
  }

  /**
   * Decodes image bytes to pixels. PNG uses the pure-Java decoder (so it works
   * in the native binary); other formats fall back to {@code javax.imageio},
   * which is JVM-only.
   */
  private static RasterImage decode(byte[] data) {
    if (PngDecoder.isPng(data)) {
      return PngDecoder.decode(data);
    }
    return decodeWithImageIo(data);
  }

  private static RasterImage decodeWithImageIo(byte[] data) {
    try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
      final BufferedImage image = ImageIO.read(in);
      if (image == null) {
        throw new PrintingException("Unsupported image format");
      }
      final int w = image.getWidth();
      final int h = image.getHeight();
      final int[] argb = new int[w * h];
      image.getRGB(0, 0, w, h, argb, 0, w);
      return new RasterImage(w, h, argb);
    } catch (IOException ex) {
      throw new PrintingException("Couldn't read image", ex);
    }
  }

  private static RasterImage scale(RasterImage src, int targetWidth, int targetHeight) {
    if (targetWidth == src.width() && targetHeight == src.height()) {
      return src;
    }
    final int[] out = new int[targetWidth * targetHeight];
    for (int y = 0; y < targetHeight; y++) {
      final int sourceY = y * src.height() / targetHeight;
      for (int x = 0; x < targetWidth; x++) {
        final int sourceX = x * src.width() / targetWidth;
        out[y * targetWidth + x] = src.argb()[sourceY * src.width() + sourceX];
      }
    }
    return new RasterImage(targetWidth, targetHeight, out);
  }
}
