//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

/**
 * The command language a printer speaks, and therefore which {@link Renderer}
 * turns the block IR into bytes for it. {@link PrinterProfile#language()}
 * carries the choice; {@link Renderers#forProfile(PrinterProfile)} dispatches
 * on
 * it.
 * <p>
 * {@link #ESC_POS} is the default for every existing profile, so adding this
 * enum changes no current behaviour.
 */
public enum PrinterLanguage {

  /** Epson ESC/POS, the default backend ({@link EscPosRenderer}). */
  ESC_POS,

  /** Star Micronics StarPRNT, e.g. the TSP143IV. */
  STAR_PRNT;

  /**
   * Whether this language answers a synchronous, ESC/POS-style status poll
   * ({@code DLE EOT}). StarPRNT has no such command (it uses push-model ASB), so
   * a Star job must skip the pre-flight probe rather than block on it; the status
   * gate keys off this.
   *
   * @return {@code true} for ESC/POS, {@code false} otherwise
   */
  public boolean supportsRealtimeStatus() {
    return this == ESC_POS;
  }
}
