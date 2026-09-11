//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.starprnt;

import java.io.ByteArrayOutputStream;
import java.util.List;

import net.nmoncho.faradn.printer.CodePage;
import net.nmoncho.faradn.printer.CodePageEncoder;
import net.nmoncho.faradn.printer.command.Code;

/**
 * Wires the shared {@link CodePageEncoder} to StarPRNT's code-page selector,
 * {@code ESC GS t n} (an extra leading {@code GS} versus ESC/POS {@code ESC t
 * n}). The {@link CodePage#id()} of a Star profile carries the Star native page
 * number (see {@code StarProfiles}), so {@link #selectPage(int)} maps it to the
 * select bytes. Verified against the StarPRNT Command Specifications (Rev
 * 4.20),
 * pp23-24.
 */
public final class StarCodePageEncoder {

  private StarCodePageEncoder() {
  }

  /**
   * The {@code ESC GS t n} bytes that select the Star page numbered {@code id}.
   */
  public static byte[] selectPage(int id) {
    return new byte[] { Code.ESC, Code.GS, 0x74, (byte) id };
  }

  /**
   * A {@link CodePageEncoder} that switches pages with {@code ESC GS t n}.
   *
   * @param out
   *        the stream to append encoded bytes and page switches to
   * @param initial
   *        the page already selected on the printer
   * @param candidates
   *        the pages that may be switched to, in preference order
   * @return the encoder
   */
  public static CodePageEncoder of(ByteArrayOutputStream out, CodePage initial, List<CodePage> candidates) {
    return new CodePageEncoder(out, initial, candidates, StarCodePageEncoder::selectPage);
  }
}
