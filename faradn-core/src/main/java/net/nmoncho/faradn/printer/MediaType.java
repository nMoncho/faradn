//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

/**
 * The print method of a label printer, a physical fact a label profile carries
 * and the label command layers map to a setup command (ZPL {@code ^MT},
 * EPL options). Irrelevant to the receipt backends.
 */
public enum MediaType {

  /** Direct thermal: heat-sensitive media, no ribbon (e.g. the ZD421d). */
  DIRECT_THERMAL,

  /** Thermal transfer: a ribbon melts onto the media (e.g. the ZD421t/c). */
  THERMAL_TRANSFER;
}
