//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt;

import java.util.List;

import net.nmoncho.faradn.document.Block;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.Renderer;

/**
 * StarPRNT rendering backend for Star Micronics printers (e.g. the TSP143IV),
 * the native counterpart to {@link net.nmoncho.faradn.printer.EscPosRenderer}.
 * <p>
 * This is a stub: the renderer-selection seam (see
 * {@link net.nmoncho.faradn.printer.Renderers}) is in place so a Star profile
 * routes here, but the byte-emitting layers land in later phases. Until then
 * {@link #render(List)} throws {@link UnsupportedOperationException}.
 */
public final class StarPrntRenderer implements Renderer {

  private final PrinterProfile profile;

  public StarPrntRenderer(PrinterProfile profile) {
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
        "StarPRNT rendering is not implemented yet (profile '" + profile.name() + "')");
  }
}
