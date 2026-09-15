//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DisplayWidthTest {

  @Test
  void asciiCountsOnePerCharacter() {
    assertEquals(0, DisplayWidth.of(""));
    assertEquals(5, DisplayWidth.of("Hello"));
    assertEquals(11, DisplayWidth.of("Total: 1,00"));
  }

  @Test
  void latinAccentsAreNarrow() {
    // Accented Latin (Latin-1 / CP1252 range) is still one column.
    assertEquals(4, DisplayWidth.of("café"));
    assertEquals(1, DisplayWidth.of("ü"));
  }

  @Test
  void kanjiHiraganaKatakanaAreWide() {
    assertEquals(2, DisplayWidth.of("漢")); // CJK ideograph
    assertEquals(4, DisplayWidth.of("漢字"));
    assertEquals(2, DisplayWidth.of("あ")); // Hiragana
    assertEquals(2, DisplayWidth.of("カ")); // Katakana
    assertEquals(2, DisplayWidth.of("한")); // Hangul syllable
    assertEquals(4, DisplayWidth.of("中文")); // Chinese
  }

  @Test
  void fullwidthFormsAndIdeographicSpaceAreWide() {
    assertEquals(2, DisplayWidth.of("Ａ")); // fullwidth A (U+FF21)
    assertEquals(2, DisplayWidth.of("　")); // ideographic space
    assertEquals(2, DisplayWidth.of("￥")); // fullwidth yen (U+FFE5)
  }

  @Test
  void mixedTextSumsPerCharacterWidths() {
    // "A" + 漢(2) + "B" = 1 + 2 + 1 = 4
    assertEquals(4, DisplayWidth.of("A漢B"));
    // "商品: " (商品 = 4, ": " = 2) then price
    assertEquals(6, DisplayWidth.of("商品: "));
  }

  @Test
  void halfwidthKatakanaIsNarrow() {
    // Half-width katakana lives in the single-byte JIS X 0201 table: one column.
    assertEquals(1, DisplayWidth.of("ｶ")); // half-width KA (U+FF76)
  }

  @Test
  void charAndCodePointOverloadsAgree() {
    assertEquals(1, DisplayWidth.of('A'));
    assertEquals(2, DisplayWidth.of('漢'));
    assertEquals(2, DisplayWidth.of(0x4E2D)); // 中
    assertEquals(1, DisplayWidth.of((int) 'x'));
  }
}
