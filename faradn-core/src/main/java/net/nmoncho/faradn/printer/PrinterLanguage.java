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
  STAR_PRNT,

  /**
   * Zebra Programming Language II, a positioned-label language (the Zebra
   * ZD421). Consumes the {@code Canvas}/{@code Placement} IR, not the receipt
   * flow, so its {@link Renderer} is the mirror of the receipt backends.
   */
  ZPL,

  /**
   * Eltron Programming Language 2, the ZD421's second native label language.
   * Also positioned-label, and single-byte code pages only.
   */
  EPL;

  /**
   * Whether this language answers a synchronous, ESC/POS-style status poll
   * ({@code DLE EOT}). StarPRNT (push-model ASB) and the label languages (ZPL,
   * EPL, whose status is request/response and deferred in v1) have no such
   * synchronous poll wired up, so those jobs skip the pre-flight probe rather
   * than block on it; the status gate keys off this.
   *
   * @return {@code true} for ESC/POS, {@code false} otherwise
   */
  public boolean supportsRealtimeStatus() {
    return this == ESC_POS;
  }
}
