package io.nmoncho.faradn;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.jsoup.nodes.Element;

/**
 * Image inside a {@link io.nmoncho.faradn.Document}
 * It supports PNG, JPEG, BMP, and WBMP.
 */
public class Image {

  // TODO test image format support. What about JPEG 2000?
  private static final Pattern BASE64_REGEX = Pattern.compile("^data:image\\/(.+?);base64,(.+?)$");

  private final Supplier<BufferedImage> supplier;
  private final Optional<Integer> height;
  private final Optional<Integer> width;

  private BufferedImage image = null;

  private Image(Supplier<BufferedImage> supplier, Optional<Integer> height, Optional<Integer> width) {
    this.supplier = supplier;
    this.height = height;
    this.width = width;
  }

  /**
   * Loads an Image element data in memory
   *
   * @return {@link BufferedImage} containing image data
   */
  BufferedImage load() {
    synchronized (this) {
      if (image == null) {
        image = supplier.get();

        // Resize if needed
        int originalHeight = image.getHeight();
        int originalWidth = image.getWidth();
        int targetHeight = height.orElse(originalHeight);
        int targetWidth = width.orElse(originalWidth);

        if (targetHeight != originalHeight || targetWidth != originalWidth) {
          java.awt.Image img = image.getScaledInstance(targetWidth, targetHeight,
              java.awt.Image.SCALE_SMOOTH);
          image = new BufferedImage(targetWidth, targetHeight, image.getType());
          image.getGraphics().drawImage(img, 0, 0, null);
        }
      }
    }

    return image;
  }

  /**
   * Creates an image element from a URL
   *
   * @param url
   *        where to the get image from
   * @return image element
   */
  public static Image fromUrl(String url) {
    return new Image(urlLoader(url), Optional.empty(), Optional.empty());
  }

  /**
   * Creates an image element from a URL, potentially scaling it based on the
   * provided `height` and `width`.
   *
   * @param url
   *        where to the get image from
   * @param height
   *        target height for the image
   * @param width
   *        target width for the image
   * @return image element
   */
  public static Image fromUrl(String url, Integer height, Integer width) {
    return new Image(urlLoader(url), Optional.of(height), Optional.of(width));
  }

  /**
   * Creates an image element from a Base64 encoding, as specified in
   * <a href="https://datatracker.ietf.org/doc/html/rfc2397">rfc2397</a>.
   *
   * @param base64
   *        Base64 encoded image
   * @return image element
   */
  public static Image fromBase64(String base64) {
    return new Image(base64Loader(base64), Optional.empty(), Optional.empty());
  }

  /**
   * Creates an image element from a Base64 encoding, as specified in
   * <a href="https://datatracker.ietf.org/doc/html/rfc2397">rfc2397</a>,
   * potentially scaling it based on the provided `height` and `width`.
   *
   * @param base64
   *        Base64 encoded image
   * @param height
   *        target height for the image
   * @param width
   *        target width for the image
   * @return image element
   */
  public static Image fromBase64(String base64, Integer height, Integer width) {
    return new Image(base64Loader(base64), Optional.of(height), Optional.of(width));
  }

  /**
   * Creates an image element from an {@link Element} object, handling either a
   * URL or a Base64 encoded image.
   *
   * @param el
   *        HTML element
   * @return Image element
   */
  public static Image fromNode(Element el) {
    if (el.tag().getName().equals("img") && !el.attr("src").trim().isEmpty()) {
      String src = el.absUrl("src");
      Optional<Integer> height = Utils.parseAttribute(el, "height");
      Optional<Integer> width = Utils.parseAttribute(el, "width");

      Matcher matcher = BASE64_REGEX.matcher(src);
      if (matcher.matches()) {
        String base64 = matcher.group(2);
        return new Image(base64Loader(base64), height, width);
      } else {
        return new Image(urlLoader(src), height, width);
      }

    } else {
      throw new PrintingException("Element [" + el + "] must be a <img /> tag, and have a valid `src` attribute");
    }
  }

  private static Supplier<BufferedImage> base64Loader(String base64) {
    return () -> {
      try {
        byte[] data = Base64.getDecoder().decode(base64);

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
          return ImageIO.read(bis);
        }

      } catch (IOException ex) {
        throw new PrintingException("Couldn't read image from Base64 form", ex);
      }
    };
  }

  private static Supplier<BufferedImage> urlLoader(String url) {
    return () -> {
      try {
        return ImageIO.read(new URL(url));
      } catch (IOException ex) {
        throw new PrintingException(String.format("Failed to read image from url [%s]", url), ex);
      }
    };
  }

}
