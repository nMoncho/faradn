//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.text;

/**
 * The on-paper column width of text, in character cells. A printer's base font
 * cell (Font&nbsp;A) is the unit: a plain ASCII/Latin glyph is one cell, but an
 * East&nbsp;Asian ideograph, kana or Hangul syllable is rendered from the
 * printer's Kanji font at twice the width (e.g. a 24&times;24 Kanji cell
 * against
 * a 12&times;24 ANK cell), so it costs two columns.
 * <p>
 * Word-wrapping and cell padding measure text in these units rather than in
 * {@link String#length()}, so a line of mixed ASCII and Kanji breaks and aligns
 * where it actually will on paper. For text with no wide characters the count
 * equals the character count, so single-byte output is unaffected.
 * <p>
 * Width classification follows the Unicode East&nbsp;Asian Width property:
 * characters that are <em>Wide</em> (W) or <em>Fullwidth</em> (F) count as two,
 * everything else as one. The ranges below cover the assigned wide blocks; an
 * unassigned or narrow code point falls through to one.
 */
public final class DisplayWidth {

  private DisplayWidth() {
  }

  /**
   * The column width of a whole string: the sum of its code points' widths.
   */
  public static int of(CharSequence text) {
    int width = 0;
    int i = 0;
    final int length = text.length();
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      width += of(codePoint);
      i += Character.charCount(codePoint);
    }
    return width;
  }

  /** The column width of a single {@code char} (a BMP code point). */
  public static int of(char ch) {
    return of((int) ch);
  }

  /**
   * The column width of a single Unicode code point: two for an East&nbsp;Asian
   * wide or fullwidth character, one otherwise.
   */
  public static int of(int codePoint) {
    return isWide(codePoint) ? 2 : 1;
  }

  private static boolean isWide(int cp) {
    return (cp >= 0x1100 && cp <= 0x115F) // Hangul Jamo
        || (cp >= 0x2E80 && cp <= 0x303E) // CJK Radicals, Kangxi, CJK symbols & punctuation
        || (cp >= 0x3041 && cp <= 0x33FF) // Hiragana, Katakana, Bopomofo, Hangul Compat Jamo, CJK compat
        || (cp >= 0x3400 && cp <= 0x4DBF) // CJK Unified Ideographs Extension A
        || (cp >= 0x4E00 && cp <= 0x9FFF) // CJK Unified Ideographs
        || (cp >= 0xA000 && cp <= 0xA4CF) // Yi Syllables and Radicals
        || (cp >= 0xAC00 && cp <= 0xD7A3) // Hangul Syllables
        || (cp >= 0xF900 && cp <= 0xFAFF) // CJK Compatibility Ideographs
        || (cp >= 0xFE30 && cp <= 0xFE4F) // CJK Compatibility Forms
        || (cp >= 0xFF00 && cp <= 0xFF60) // Fullwidth Forms
        || (cp >= 0xFFE0 && cp <= 0xFFE6) // Fullwidth signs
        || (cp >= 0x1B000 && cp <= 0x1B2FF) // Kana Supplement, Kana Extended, Small Kana
        || (cp >= 0x20000 && cp <= 0x3FFFD); // CJK Unified Ideographs Extension B and beyond
  }
}
