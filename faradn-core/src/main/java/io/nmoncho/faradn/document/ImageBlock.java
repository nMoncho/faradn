package io.nmoncho.faradn.document;

import io.nmoncho.faradn.Image;

/**
 * An image occupying its own vertical band of the receipt.
 */
public record ImageBlock(Image image, ComputedStyle.Alignment alignment) implements Block {

  public ImageBlock {
    if (image == null) {
      throw new IllegalArgumentException("image must not be null");
    }
    if (alignment == null) {
      throw new IllegalArgumentException("alignment must not be null");
    }
  }
}
