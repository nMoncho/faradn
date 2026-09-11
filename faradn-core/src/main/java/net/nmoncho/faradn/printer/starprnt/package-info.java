//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

/**
 * Internal StarPRNT byte-emission layer for Star Micronics printers (the
 * TSP143IV): the {@link net.nmoncho.faradn.printer.starprnt.StarPrntRenderer}
 * and its encoding machinery
 * ({@link net.nmoncho.faradn.printer.starprnt.StarCodePageEncoder},
 * {@link net.nmoncho.faradn.printer.starprnt.StarRasterizer}), the native
 * counterpart to the {@code escpos} package. Built on the shared, protocol-
 * neutral helpers under {@code printer.command} / {@code printer.text} /
 * {@code printer.image}.
 * <p>
 * <strong>Not part of the public API.</strong> These types are implementation
 * details and may change without notice. Render through
 * {@link net.nmoncho.faradn.Document}, {@link net.nmoncho.faradn.Printer}, or
 * {@link net.nmoncho.faradn.printer.Renderers}.
 */
package net.nmoncho.faradn.printer.starprnt;
