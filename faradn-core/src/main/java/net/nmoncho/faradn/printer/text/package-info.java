//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

/**
 * Protocol-neutral text layout helpers shared by every renderer:
 * {@link net.nmoncho.faradn.printer.text.TextWrapper} (column word-wrap over
 * styled runs) and {@link net.nmoncho.faradn.printer.text.BoxDrawing} (Unicode
 * line glyphs for borders). Neither emits any command bytes, so they are reused
 * verbatim by the ESC/POS and StarPRNT backends.
 * <p>
 * <strong>Not part of the public API.</strong> Implementation detail; may
 * change
 * without notice.
 */
package net.nmoncho.faradn.printer.text;
