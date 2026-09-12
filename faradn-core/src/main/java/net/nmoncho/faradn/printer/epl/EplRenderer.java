//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import java.util.List;

import net.nmoncho.faradn.document.Block;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.Renderer;

/**
 * Renders the positioned-label IR ({@code Canvas}/{@code Placement}) into EPL2
 * bytes for a Zebra printer (the ZD421), the ZD421's second native label
 * language alongside {@link net.nmoncho.faradn.printer.zpl.ZplRenderer}. Like
 * the ZPL renderer it consumes a {@code Canvas} and rejects the receipt-flow
 * blocks; it differs mainly in the command syntax (line-based ASCII) and in
 * being single-byte code pages only.
 * <p>
 * <strong>Phase 0 stub.</strong> Only the renderer-selection seam
 * ({@link net.nmoncho.faradn.printer.Renderers#forProfile}) is wired so far;
 * {@link #render(List)} is implemented in Phase 5 (see
 * {@code PLAN_ZEBRA_ZD421.md}).
 */
public final class EplRenderer implements Renderer {

  private final PrinterProfile profile;

  public EplRenderer(PrinterProfile profile) {
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
        "EplRenderer is not implemented yet (PLAN_ZEBRA_ZD421.md Phase 5)");
  }
}
