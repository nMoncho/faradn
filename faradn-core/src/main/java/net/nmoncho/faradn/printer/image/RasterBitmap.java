//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.image;

/**
 * A packed 1-bit bitmap ready to hand to a printer's raster command: the
 * dithered
 * pixels MSB-first, eight horizontal dots per byte, top to bottom, a set bit
 * meaning black. It is language-neutral - the header that precedes
 * {@link #body()}
 * differs per protocol (ESC/POS {@code GS v 0} vs StarPRNT {@code ESC GS S}).
 *
 * @param width
 *        image width in dots
 * @param height
 *        image height in dots (rows)
 * @param bytesPerRow
 *        {@code ceil(width / 8)}; {@code body.length == height * bytesPerRow}
 * @param body
 *        the packed pixel bytes, row-major
 */
public record RasterBitmap(int width, int height, int bytesPerRow, byte[] body) {
}
