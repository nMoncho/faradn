//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import java.util.List;

import net.nmoncho.faradn.document.Block;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.Renderer;

/**
 * Renders the positioned-label IR ({@code Canvas}/{@code Placement}) into ZPL
 * II
 * bytes for a Zebra printer (the ZD421). It is the label-language counterpart
 * to
 * the receipt-flow {@link net.nmoncho.faradn.printer.EscPosRenderer} and
 * {@code StarPrntRenderer}, and their structural mirror: it consumes a
 * {@code Canvas} and rejects the receipt-flow blocks.
 * <p>
 * <strong>Phase 0 stub.</strong> Only the renderer-selection seam
 * ({@link net.nmoncho.faradn.printer.Renderers#forProfile}) is wired so far;
 * {@link #render(List)} is implemented in Phase 3 (see
 * {@code PLAN_ZEBRA_ZD421.md}).
 */
public final class ZplRenderer implements Renderer {

  private final PrinterProfile profile;

  public ZplRenderer(PrinterProfile profile) {
    if (profile == null) {
      throw new IllegalArgumentException("profile must not be null");
    }
    this.profile = profile;
  }

  /** The profile this renderer targets. */
  public PrinterProfile profile() {
    return profile;
  }

  @Override
  public byte[] render(List<Block> blocks) {
    throw new UnsupportedOperationException(
        "ZplRenderer is not implemented yet (PLAN_ZEBRA_ZD421.md Phase 3)");
  }
}
