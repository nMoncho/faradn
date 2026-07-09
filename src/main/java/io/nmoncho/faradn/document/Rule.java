package io.nmoncho.faradn.document;

/**
 * A horizontal rule (the {@code hr} tag): a full-width separator line. How
 * it is realized (dashes, box-drawing characters, a thin raster strip) is
 * up to the renderer.
 */
public record Rule() implements Block {
}
