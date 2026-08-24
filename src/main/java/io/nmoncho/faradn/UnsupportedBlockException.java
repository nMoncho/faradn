package io.nmoncho.faradn;

import io.nmoncho.faradn.document.Block;

/**
 * Thrown when the renderer meets a {@link Block} it cannot yet turn into
 * printer commands. Images and barcodes are realized in a later milestone;
 * until then they raise this rather than being silently dropped.
 */
public class UnsupportedBlockException extends PrintingException {

  public UnsupportedBlockException(Block block) {
    super("Rendering [" + block.getClass().getSimpleName() + "] is not supported yet");
  }
}
