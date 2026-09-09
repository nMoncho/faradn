//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Image inside a {@link Document}, decoded to {@link RasterImage} pixels.
 * <p>
 * PNGs are decoded in pure Java ({@link PngDecoder}) so they work in the native
 * binary; other formats (JPEG, BMP, WBMP) fall back to {@code javax.imageio},
 * which is available on the JVM but not inside the GraalVM native image.
 * </p>
 */
public class Image {

  private static final Pattern BASE64_REGEX = Pattern.compile("^data:image\\/(.+?);base64,(.+?)$");

  // Secure by default: only data: URIs are decoded, so untrusted HTML cannot make
  // the process fetch an arbitrary URL (SSRF). Widen with policy(...) for trusted
  // input only. Volatile: this is a process-wide security posture set at startup.
  private static volatile ImagePolicy policy = ImagePolicy.DATA_URIS_ONLY;

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
   * Sets the process-wide {@link ImagePolicy} governing how {@code <img src>}
   * URLs
   * are fetched. The default is {@link ImagePolicy#DATA_URIS_ONLY} (fetch
   * nothing); widen it only when the HTML being rendered is trusted.
   *
   * @param imagePolicy
   *        the policy to apply
   */
  public static void policy(ImagePolicy imagePolicy) {
    if (imagePolicy == null) {
      throw new IllegalArgumentException("policy must not be null");
    }
    policy = imagePolicy;
  }

  /** The current image-fetch policy. */
  public static ImagePolicy policy() {
    return policy;
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
   * Creates an image from an already-resolved {@code src} - either a
   * {@code data:} URI (Base64) or a URL - with optional target dimensions.
   *
   * @param src
   *        a {@code data:image/...;base64,...} URI or an image URL
   * @param height
   *        target height, or {@code null} to keep the decoded height
   * @param width
   *        target width, or {@code null} to keep the decoded width
   * @return image element
   */
  public static Image fromSrc(String src, Integer height, Integer width) {
    final Matcher matcher = BASE64_REGEX.matcher(src);
    final Supplier<RasterImage> loader = matcher.matches() ? base64Loader(matcher.group(2)) : urlLoader(src);
    return new Image(loader, Optional.ofNullable(height), Optional.ofNullable(width));
  }

  private static Supplier<RasterImage> base64Loader(String base64) {
    return () -> decode(Base64.getDecoder().decode(base64));
  }

  private static Supplier<RasterImage> urlLoader(String url) {
    return () -> fetch(url);
  }

  /**
   * Fetches an image URL under the current {@link ImagePolicy}: the scheme must
   * be
   * allowed (nothing but {@code data:} by default), network fetches use the
   * policy's timeouts and do not follow redirects, and the read is capped at the
   * policy's byte limit. A disallowed scheme throws rather than reaching out.
   */
  private static RasterImage fetch(String url) {
    final ImagePolicy pol = policy;
    final String scheme = schemeOf(url);
    if (!pol.allows(scheme)) {
      throw new PrintingException("image URL scheme [" + scheme
          + "] is not permitted by the current image policy; only data: URIs are allowed by default");
    }
    try {
      final URLConnection connection = new URL(url).openConnection();
      if (connection instanceof HttpURLConnection http) {
        http.setInstanceFollowRedirects(false); // a redirect could reach an unallowed target
      }
      if (pol.connectTimeoutMillis() > 0) {
        connection.setConnectTimeout(pol.connectTimeoutMillis());
      }
      if (pol.readTimeoutMillis() > 0) {
        connection.setReadTimeout(pol.readTimeoutMillis());
      }
      connection.setUseCaches(false);
      try (InputStream in = connection.getInputStream()) {
        return decode(readCapped(in, pol.maxBytes(), url));
      }
    } catch (IOException ex) {
      throw new PrintingException("Failed to read image from url [" + url + "]", ex);
    }
  }

  private static String schemeOf(String url) {
    final int colon = url.indexOf(':');
    return colon > 0 ? url.substring(0, colon).toLowerCase(Locale.ROOT) : "";
  }

  private static byte[] readCapped(InputStream in, long maxBytes, String url) throws IOException {
    final int limit = (int) Math.min(maxBytes, Integer.MAX_VALUE - 8);
    final byte[] data = in.readNBytes(limit + 1);
    if (data.length > limit) {
      throw new PrintingException("image at [" + url + "] exceeds the maximum allowed size of " + maxBytes + " bytes");
    }
    return data;
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
    try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      if (stream == null) {
        throw new PrintingException("Unsupported image format");
      }
      final Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) {
        throw new PrintingException("Unsupported image format");
      }
      final ImageReader reader = readers.next();
      try {
        reader.setInput(stream);
        // Read the declared dimensions from the header and reject a bomb before
        // reader.read() allocates the full (attacker-controlled) pixel buffer.
        RasterImage.checkDimensions(reader.getWidth(0), reader.getHeight(0));
        final BufferedImage image = reader.read(0);
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int[] argb = new int[w * h];
        image.getRGB(0, 0, w, h, argb, 0, w);
        return new RasterImage(w, h, argb);
      } finally {
        reader.dispose();
      }
    } catch (IOException ex) {
      throw new PrintingException("Couldn't read image", ex);
    }
  }

  private static RasterImage scale(RasterImage src, int targetWidth, int targetHeight) {
    if (targetWidth == src.width() && targetHeight == src.height()) {
      return src;
    }
    // The target size can come from attacker-controlled width/height attributes.
    RasterImage.checkDimensions(targetWidth, targetHeight);
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
