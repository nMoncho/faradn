//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

/**
 * A small, protocol-neutral DSL for building printer command byte sequences:
 * {@link net.nmoncho.faradn.printer.command.Code} and its
 * {@link net.nmoncho.faradn.printer.command.SimpleCode} /
 * {@link net.nmoncho.faradn.printer.command.ParametricCode} /
 * {@link net.nmoncho.faradn.printer.command.BooleanCode} implementations over
 * {@link net.nmoncho.faradn.printer.command.Byteable} parameters. No type here
 * hardcodes an ESC/POS opcode, so both the ESC/POS and StarPRNT command layers
 * are built on it.
 * <p>
 * <strong>Not part of the public API.</strong> Implementation detail; may
 * change
 * without notice.
 */
package net.nmoncho.faradn.printer.command;
