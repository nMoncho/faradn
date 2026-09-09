package net.nmoncho.faradn.printer;

import java.util.List;

import net.nmoncho.faradn.document.Block;

/**
 * Renders the intermediate representation ({@code List<Block>}) into printer
 * bytes.
 * <p>
 * {@link EscPosRenderer} is the ESC/POS implementation; this interface is the
 * stable seam an alternative backend (a different printer language) would
 * implement, so callers can depend on {@code Renderer} rather than a concrete
 * class.
 * <p>
 * Implementations are expected to be stateless and immutable, and therefore
 * safe
 * to share between threads.
 */
public interface Renderer {

  /**
   * Renders a block sequence into a complete print job.
   *
   * @param blocks
   *        the intermediate representation, in reading order (see
   *        {@link net.nmoncho.faradn.Document#blocks()})
   * @return the printer bytes to deliver over a
   *         {@link net.nmoncho.faradn.transport.Transport}
   */
  byte[] render(List<Block> blocks);
}
