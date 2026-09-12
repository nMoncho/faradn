//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

import net.nmoncho.faradn.printer.PrinterLanguage;

/**
 * Picks the {@link StatusReader} for a printer's {@link PrinterLanguage}, the
 * status-channel counterpart to {@code Renderers.forProfile}. A {@code switch}
 * is enough for the two languages today; it upgrades to a registry later
 * without
 * touching callers.
 */
public final class StatusReaders {

  private static final StatusReader ESC_POS = new EscPosStatusReader();
  private static final StatusReader STAR_PRNT = new StarStatusReader();

  private StatusReaders() {
  }

  /**
   * The status reader for a command language.
   *
   * @param language
   *        the printer's command language
   * @return the reader that speaks its status protocol
   */
  public static StatusReader forLanguage(PrinterLanguage language) {
    if (language == null) {
      throw new IllegalArgumentException("language must not be null");
    }
    return switch (language) {
      case ESC_POS -> ESC_POS;
      case STAR_PRNT -> STAR_PRNT;
    };
  }
}
