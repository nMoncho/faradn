package net.nmoncho.faradn.document;

/**
 * Content that can be placed at an absolute position inside a {@link Canvas}
 * (a page-mode print area). The permitted records also implement {@link Block},
 * so the same value is usable in the flow IR and in a positioned canvas.
 */
public sealed interface Placeable permits Paragraph, ImageBlock, Barcode {
}
