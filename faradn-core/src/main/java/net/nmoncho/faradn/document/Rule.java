//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

/**
 * A horizontal rule (the {@code hr} tag): a full-width separator line. How
 * it is realized (dashes, box-drawing characters, a thin raster strip) is
 * up to the renderer.
 */
public record Rule() implements Block {
}
