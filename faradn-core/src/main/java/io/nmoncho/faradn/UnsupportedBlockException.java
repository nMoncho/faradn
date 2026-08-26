package io.nmoncho.faradn;

import io.nmoncho.faradn.document.Block;

/** Thrown when the renderer meets a {@link Block} it cannot yet turn into printer commands.
 */
public class UnsupportedBlockException extends PrintingException {

  public UnsupportedBlockException(Block block) {
    super("Rendering [" + block.getClass().getSimpleName() + "] is not supported yet");
  }
}
