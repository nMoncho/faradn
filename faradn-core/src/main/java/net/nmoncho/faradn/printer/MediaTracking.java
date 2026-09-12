//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

/**
 * How a label printer finds the top of each label, a physical fact a label
 * profile carries and the label command layers map to a media-tracking command
 * (ZPL {@code ^MN}, EPL {@code Q} gap). Irrelevant to the receipt backends.
 */
public enum MediaTracking {

  /** Continuous stock with no gaps or marks; the label length governs. */
  CONTINUOUS,

  /** Die-cut labels separated by a gap or web sensed between them. */
  GAP,

  /** Tag stock with a printed black mark (or notch) between labels. */
  MARK;
}
