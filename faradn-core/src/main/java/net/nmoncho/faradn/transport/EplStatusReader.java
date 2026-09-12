//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.transport;

/**
 * Reads Zebra EPL readiness.
 * <p>
 * <strong>Phase 0 stub.</strong> In v1
 * {@link net.nmoncho.faradn.printer.PrinterLanguage#supportsRealtimeStatus()}
 * is
 * {@code false} for EPL, so no pre-flight probe runs and this reader is never
 * invoked; it exists to keep {@link StatusReaders#forLanguage} total over the
 * enum. The real {@code ^ee} decode (driving {@link Transport#exchange}) lands
 * in a later phase (see {@code PLAN_ZEBRA_ZD421.md} Section 7); EPL status is
 * documented as RS-232 only, so its behaviour over USB/9100 must be confirmed
 * on
 * hardware first. Until then it optimistically reports
 * {@link PrinterStatus#READY}
 * so it can never falsely block a job.
 */
public final class EplStatusReader implements StatusReader {

  @Override
  public PrinterStatus read(Transport transport) {
    return PrinterStatus.READY;
  }
}
