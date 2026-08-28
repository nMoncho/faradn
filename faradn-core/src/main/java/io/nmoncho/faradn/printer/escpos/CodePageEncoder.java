package io.nmoncho.faradn.printer.escpos;

import java.io.ByteArrayOutputStream;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.nmoncho.faradn.printer.CodePage;

/**
 * Encodes text to ESC/POS bytes, switching code pages on the fly.
 * <p>
 * ESC/POS selects one code page at a time ({@code ESC t n}); a character
 * outside it would otherwise encode to the replacement byte {@code '?'}. This
 * encoder walks the text and, per character, keeps the currently selected page
 * when it can encode the character and otherwise switches to the first
 * candidate page that can - emitting the {@code ESC t} command inline. Because
 * the current page is always preferred, a run of characters in one script
 * costs a single switch, and ASCII never forces one. A character no candidate
 * page can encode falls back to {@code '?'} in the current page.
 */
final class CodePageEncoder {

  private final ByteArrayOutputStream out;
  private final List<CodePage> candidates;
  private final Map<CodePage, CharsetEncoder> encoders = new EnumMap<>(CodePage.class);
  private CodePage current;

  /**
   * @param out
   *        the stream to append encoded bytes (and {@code ESC t} switches) to
   * @param initial
   *        the page already selected on the printer (tried first, so no switch
   *        is emitted for text it can encode)
   * @param candidates
   *        the pages that may be switched to, in preference order
   */
  CodePageEncoder(ByteArrayOutputStream out, CodePage initial, List<CodePage> candidates) {
    this.out = out;
    this.current = initial;
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

  /**
   * Encodes {@code text}, writing any needed {@code ESC t} switches and the
   * bytes.
   */
  void emit(String text) {
    int i = 0;
    final int length = text.length();

    while (i < length) {
      final int codePoint = text.codePointAt(i);
      final int width = Character.charCount(codePoint);
      final String unit = text.substring(i, i + width);
      final CodePage page = choose(unit);
      if (page != null && page != current) {
        out.writeBytes(new byte[] { Code.ESC, 0x74, (byte) page.id() });
        current = page;
      }
      // When no candidate can encode the unit, keep the current page and let it
      // map the character to its replacement byte ('?').
      out.writeBytes(unit.getBytes(current.charset()));
      i += width;
    }
  }

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
}
