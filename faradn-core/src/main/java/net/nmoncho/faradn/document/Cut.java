//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.document;

/**
 * Cuts the paper. A full cut ({@code partial} false) severs the receipt
 * completely; a partial cut leaves one or more small bridges of paper so the
 * receipt stays attached until torn off.
 * <p>
 * {@code points} is how many bridges a partial cut leaves, {@code 1} or
 * {@code 3}. It maps to distinct ESC/POS commands - one point is {@code GS V 1}
 * ({@code ESC i}), three points is {@code ESC m} - and applies only to a
 * partial
 * cut (a full cut ignores it). Three-point partial is an Epson feature;
 * StarPRNT
 * has a single partial cut ({@code ESC d 1}), so it renders both point counts
 * the same.
 *
 * @param partial
 *        whether to leave the receipt attached (a partial cut) rather than
 *        severing it (a full cut)
 * @param points
 *        the number of connecting bridges a partial cut leaves, {@code 1} or
 *        {@code 3}
 */
public record Cut(boolean partial, int points) implements Block {

  public Cut {
    if (points != 1 && points != 3) {
      throw new IllegalArgumentException("cut points must be 1 or 3, got " + points);
    }
  }

  /**
   * A cut with the default single connecting point for a partial cut.
   *
   * @param partial
   *        whether to leave the receipt attached (a partial cut)
   */
  public Cut(boolean partial) {
    this(partial, 1);
  }
}
