//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import net.nmoncho.faradn.printer.epl.EplRenderer;
import net.nmoncho.faradn.printer.starprnt.StarPrntRenderer;
import net.nmoncho.faradn.printer.zpl.ZplRenderer;

/**
 * Picks the {@link Renderer} for a profile's {@link PrinterLanguage}. This is
 * the single seam every call site goes through, so a job renders in the
 * language
 * its target printer actually speaks.
 * <p>
 * ESC/POS is the default (see {@link PrinterProfile#language()}), so profiles
 * that don't opt into another language keep using {@link EscPosRenderer}
 * unchanged. A {@code switch} is enough for the backends today; it upgrades
 * to a registry later without touching the call sites.
 */
public final class Renderers {

  private Renderers() {
  }

  /**
   * The renderer for a profile, chosen by its {@link PrinterProfile#language()}.
   *
   * @param profile
   *        the target printer's profile
   * @return a renderer that emits that printer's command language
   */
  public static Renderer forProfile(PrinterProfile profile) {
    if (profile == null) {
      throw new IllegalArgumentException("profile must not be null");
    }
    return switch (profile.language()) {
      case ESC_POS -> new EscPosRenderer(profile);
      case STAR_PRNT -> new StarPrntRenderer(profile);
      case ZPL -> new ZplRenderer(profile);
      case EPL -> new EplRenderer(profile);
    };
  }
}
