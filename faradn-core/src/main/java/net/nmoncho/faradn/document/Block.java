package net.nmoncho.faradn.document;

/**
 * A block-level element of the intermediate representation (IR).
 * <p>
 * ESC/POS is a line-oriented protocol, so the IR is a flat sequence of
 * blocks rather than a general box-model tree: each block occupies one or
 * more full lines of paper. Renderers walk {@code List<Block>} and emit
 * printer bytes; nothing in this package knows about ESC/POS commands.
 */
public sealed interface Block permits Paragraph, ImageBlock, Barcode, Rule, Feed, Cut, Table {
}
