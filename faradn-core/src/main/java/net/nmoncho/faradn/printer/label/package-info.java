//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

/**
 * Protocol-neutral layout helpers shared by the positioned-label backends (ZPL
 * and EPL): {@link net.nmoncho.faradn.printer.label.LabelLayout} (the
 * whole-canvas rotation transform, the dot-to-column bridge over
 * {@link net.nmoncho.faradn.printer.text.TextWrapper}, and mixed-run field
 * segmentation) and {@link net.nmoncho.faradn.printer.label.HexEncoder} (ASCII
 * hex for ZPL {@code ^GFA} graphics). Neither emits any language command bytes,
 * so both are reused by the ZPL and EPL renderers.
 * <p>
 * <strong>Not part of the public API.</strong> Implementation detail; may
 * change without notice.
 */
package net.nmoncho.faradn.printer.label;
