//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Encodes text to printer bytes, switching code pages on the fly.
 * <p>
 * A printer selects one code page at a time; a character outside it would
 * otherwise encode to the replacement byte {@code '?'}. This encoder walks the
 * text and, per character, keeps the currently selected page when it can encode
 * the character and otherwise switches to the first candidate page that can -
 * emitting the page-select command inline. Because the current page is always
 * preferred, a run of characters in one script costs a single switch, and ASCII
 * never forces one. A character no candidate page can encode falls back to
 * {@code '?'} in the current page.
 * <p>
 * The page-select bytes are the one language-specific part, supplied as a
 * {@code selectPage} function: ESC/POS passes {@code id -> {ESC, 0x74, id}}
 * ({@code ESC t n}), StarPRNT {@code id -> {ESC, GS, 0x74, id}}
 * ({@code ESC GS t n}). Everything else - the greedy per-character page choice
 * -
 * is protocol-neutral.
 * <p>
 * <strong>Multi-byte (Kanji) text.</strong> Single-byte code pages cannot
 * encode CJK ideographs, kana or Hangul; those need the printer's Kanji ROM,
 * reached by a separate stateful mode (ESC/POS {@code FS &} / {@code FS .},
 * StarPRNT {@code ESC $ 1} / {@code ESC $ 0}). When a {@link MultibyteMode} is
 * supplied, a character no single-byte page can encode but the multi-byte
 * charset can is emitted in that mode: the encoder enters Kanji mode (once,
 * selecting the code system first if the mode requires it), writes the
 * multi-byte code, and leaves Kanji mode before the next single-byte character.
 * This keeps single-byte code-page selection and multi-byte mode independent,
 * so
 * mixed ASCII/Latin and Kanji text prints correctly. Call {@link #finish()} at
 * end of job to leave Kanji mode if a trailing Kanji run left it on.
 */
public final class CodePageEncoder {

  private final ByteArrayOutputStream out;
  private final List<CodePage> candidates;
  private final IntFunction<byte[]> selectPage;
  private final Map<CodePage, CharsetEncoder> encoders = new HashMap<>();
  private CodePage current;

  private final MultibyteMode multibyte;
  private boolean kanjiMode;
  private boolean codeSystemSelected;

  /**
   * @param out
   *        the stream to append encoded bytes (and page switches) to
   * @param initial
   *        the page already selected on the printer (tried first, so no switch
   *        is emitted for text it can encode)
   * @param candidates
   *        the pages that may be switched to, in preference order
   * @param selectPage
   *        maps a {@link CodePage#id()} to the command bytes that select it
   */
  public CodePageEncoder(ByteArrayOutputStream out, CodePage initial, List<CodePage> candidates,
      IntFunction<byte[]> selectPage) {
    this(out, initial, candidates, selectPage, null);
  }

  /**
   * @param out
   *        the stream to append encoded bytes (and page switches) to
   * @param initial
   *        the page already selected on the printer (tried first, so no switch
   *        is emitted for text it can encode)
   * @param candidates
   *        the pages that may be switched to, in preference order
   * @param selectPage
   *        maps a {@link CodePage#id()} to the command bytes that select it
   * @param multibyte
   *        the printer's multi-byte (Kanji) mode, or {@code null} when the
   *        printer has no Kanji ROM (CJK characters then fall back to
   *        {@code '?'})
   */
  public CodePageEncoder(ByteArrayOutputStream out, CodePage initial, List<CodePage> candidates,
      IntFunction<byte[]> selectPage, MultibyteMode multibyte) {
    this.out = out;
    this.current = initial;
    this.selectPage = selectPage;
    this.multibyte = multibyte;
    // Try the already-selected page first, then the rest in preference order.
    final List<CodePage> ordered = new ArrayList<>();
    ordered.add(initial);
    for (CodePage page : candidates) {
      if (!ordered.contains(page)) {
        ordered.add(page);
      }
    }
    this.candidates = List.copyOf(ordered);
  }

  /** The page currently selected on the printer. */
  CodePage current() {
    return current;
  }

  /** Whether the encoder currently has Kanji (multi-byte) mode turned on. */
  boolean kanjiMode() {
    return kanjiMode;
  }

  /**
   * Encodes {@code text}, writing any needed page switches and the bytes.
   */
  public void emit(String text) {
    int i = 0;
    final int length = text.length();

    while (i < length) {
      final int codePoint = text.codePointAt(i);
      final int width = Character.charCount(codePoint);
      final String unit = text.substring(i, i + width);

      final CodePage page = choose(unit);
      if (page != null) {
        // A single-byte page can encode it: leave Kanji mode and select the page.
        leaveKanjiMode();
        if (page.id() != current.id()) {
          out.writeBytes(selectPage.apply(page.id()));
          current = page;
        }
        out.writeBytes(unit.getBytes(current.charset()));
      } else if (multibyte != null && multibyte.canEncode(unit)) {
        // No single-byte page can, but the Kanji ROM can: enter Kanji mode.
        enterKanjiMode();
        out.writeBytes(unit.getBytes(multibyte.charset()));
      } else {
        // Nothing can encode it: keep the current single-byte page and let it map
        // the character to its replacement byte ('?').
        leaveKanjiMode();
        out.writeBytes(unit.getBytes(current.charset()));
      }
      i += width;
    }
  }

  /**
   * Leaves Kanji mode if a trailing multi-byte run left it on, restoring the
   * single-byte baseline. Call once at end of job, before the cut.
   */
  public void finish() {
    leaveKanjiMode();
  }

  private void enterKanjiMode() {
    if (kanjiMode) {
      return;
    }
    if (!codeSystemSelected) {
      out.writeBytes(multibyte.selectCodeSystem());
      codeSystemSelected = true;
    }
    out.writeBytes(multibyte.enter());
    kanjiMode = true;
  }

  private void leaveKanjiMode() {
    if (kanjiMode) {
      out.writeBytes(multibyte.leave());
      kanjiMode = false;
    }
  }

  // TODO: this is a greedy, per-character choice that prefers the current page,
  // so it minimizes switches locally but not globally. A future version could
  // scan the whole text up front and pick, for each character, a page that
  // minimizes the total number of ESC t switches (e.g. a shortest-path / DP
  // over the set of pages each character can encode), trading a little compute
  // for shorter output on mixed-script text.
  private CodePage choose(String unit) {
    if (canEncode(current, unit)) {
      return current;
    }

    for (CodePage page : candidates) {
      if (canEncode(page, unit)) {
        return page;
      }
    }
    return null;
  }

  private boolean canEncode(CodePage page, String unit) {
    return encoders.computeIfAbsent(page, p -> p.charset().newEncoder()).canEncode(unit);
  }

  /**
   * A printer's multi-byte (Kanji) text mode: the charset that encodes CJK
   * characters the way the printer's Kanji ROM decodes them, plus the
   * language-specific command bytes that turn the mode on and off.
   * <p>
   * The bytes differ by language: ESC/POS enters with {@code FS &} and leaves
   * with {@code FS .}, selecting the code system once up front with {@code FS C}
   * (JIS for the collision-safe {@code x-JIS0208}, Shift-JIS for Shift-JIS
   * charsets); StarPRNT toggles Shift-JIS mode with {@code ESC $ 1} /
   * {@code ESC $ 0} and needs no separate code-system select. {@code enter} and
   * {@code leave} bracket every multi-byte run; single-byte text is always
   * emitted with the mode off, so high bytes from a Latin code page are never
   * mistaken for a Kanji lead byte.
   */
  public static final class MultibyteMode {

    private final Charset charset;
    private final byte[] enter;
    private final byte[] leave;
    private final byte[] selectCodeSystem;
    private CharsetEncoder encoder;

    /**
     * @param charset
     *        the multi-byte charset the printer's Kanji ROM decodes
     * @param enter
     *        the bytes that turn Kanji mode on
     * @param leave
     *        the bytes that turn Kanji mode off
     * @param selectCodeSystem
     *        the bytes that select the Kanji code system, emitted once before the
     *        first {@code enter}; empty when the mode needs none
     */
    public MultibyteMode(Charset charset, byte[] enter, byte[] leave, byte[] selectCodeSystem) {
      if (charset == null) {
        throw new IllegalArgumentException("charset must not be null");
      }
      this.charset = charset;
      this.enter = enter.clone();
      this.leave = leave.clone();
      this.selectCodeSystem = selectCodeSystem.clone();
    }

    Charset charset() {
      return charset;
    }

    byte[] enter() {
      return enter.clone();
    }

    byte[] leave() {
      return leave.clone();
    }

    byte[] selectCodeSystem() {
      return selectCodeSystem.clone();
    }

    boolean canEncode(String unit) {
      if (encoder == null) {
        encoder = charset.newEncoder();
      }
      return encoder.canEncode(unit);
    }
  }
}
