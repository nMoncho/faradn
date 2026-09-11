//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

/**
 * StarPRNT command dictionaries for the TSP143IV, the parallel of
 * {@code printer.escpos.commands}: each class holds the concrete Star opcodes
 * (character style, justification, feed, line spacing, cut/drawer, barcodes),
 * built on the shared DSL under {@code printer.command}. Opcodes are verified
 * against the StarPRNT Command Specifications (Rev 4.20), cited per class.
 * <p>
 * <strong>Not part of the public API.</strong> Implementation detail; may
 * change
 * without notice.
 */
package net.nmoncho.faradn.printer.starprnt.commands;
