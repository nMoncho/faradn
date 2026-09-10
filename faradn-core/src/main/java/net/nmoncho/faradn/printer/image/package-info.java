//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

/**
 * Protocol-neutral image rasterization:
 * {@link net.nmoncho.faradn.printer.image.RasterEncoder} scales, dithers, and
 * packs an image into a 1-bit
 * {@link net.nmoncho.faradn.printer.image.RasterBitmap}.
 * The packed body is identical across backends; only the raster command header
 * differs (ESC/POS {@code GS v 0}, StarPRNT {@code ESC GS S}) and is prepended
 * by
 * the caller.
 * <p>
 * <strong>Not part of the public API.</strong> Implementation detail; may
 * change
 * without notice.
 */
package net.nmoncho.faradn.printer.image;
