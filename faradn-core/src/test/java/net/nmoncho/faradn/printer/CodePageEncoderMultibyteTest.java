//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.CodePageEncoder.MultibyteMode;

/**
 * The multi-byte (Kanji) path of {@link CodePageEncoder}: entering and leaving
 * Kanji mode around CJK characters while single-byte text stays on the normal
 * code-page path. Uses the ESC/POS byte shapes ({@code FS C 0} / {@code FS &} /
 * {@code FS .}) with the collision-safe {@code x-JIS0208} charset.
 */
class CodePageEncoderMultibyteTest {

  private static final CodePage CP437 = new CodePage(0, Charset.forName("IBM437"));

  private static final byte[] ENTER = { 0x1C, 0x26 }; // FS &
  private static final byte[] LEAVE = { 0x1C, 0x2E }; // FS .
  private static final byte[] SELECT = { 0x1C, 0x43, 0x00 }; // FS C 0 (JIS code system)

  private static CodePageEncoder encoder(ByteArrayOutputStream out) {
    final MultibyteMode kanji = new MultibyteMode(Charset.forName("x-JIS0208"), ENTER, LEAVE, SELECT);
    return new CodePageEncoder(out, CP437, List.of(CP437), id -> new byte[] { 0x1B, 0x74, (byte) id }, kanji);
  }

  @Test
  void asciiEmitsNoKanjiCommands() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    encoder(out).emit("Hi");

    // A Kanji ROM is configured, but pure ASCII never enters Kanji mode.
    assertArrayEquals("Hi".getBytes(StandardCharsets.US_ASCII), out.toByteArray());
  }

  @Test
  void mixedTextBracketsKanjiWithModeToggles() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePageEncoder enc = encoder(out);
    enc.emit("A漢B");
    enc.finish();

    // A | FS C 0 | FS & | JIS0208(漢)=34 41 | FS . | B
    assertArrayEquals(new byte[] {
        'A',
        0x1C, 0x43, 0x00, // FS C 0
        0x1C, 0x26, // FS &
        0x34, 0x41, // 漢
        0x1C, 0x2E, // FS .
        'B'
    }, out.toByteArray());
  }

  @Test
  void consecutiveKanjiEnterModeOnce() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePageEncoder enc = encoder(out);
    enc.emit("漢字");
    enc.finish();

    // One FS C 0, one FS &, both glyphs, one FS . at finish.
    assertArrayEquals(new byte[] {
        0x1C, 0x43, 0x00, // FS C 0
        0x1C, 0x26, // FS &
        0x34, 0x41, // 漢
        0x3B, 0x7A, // 字
        0x1C, 0x2E // FS . (from finish)
    }, out.toByteArray());
  }

  @Test
  void codeSystemSelectedOnlyOnceAcrossRuns() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePageEncoder enc = encoder(out);
    enc.emit("漢A漢"); // Kanji, ASCII, Kanji
    enc.finish();

    assertArrayEquals(new byte[] {
        0x1C, 0x43, 0x00, // FS C 0 (once, before the first FS &)
        0x1C, 0x26, // FS &
        0x34, 0x41, // 漢
        0x1C, 0x2E, // FS .
        'A',
        0x1C, 0x26, // FS & again (no second FS C)
        0x34, 0x41, // 漢
        0x1C, 0x2E // FS . (from finish)
    }, out.toByteArray());
  }

  @Test
  void finishLeavesKanjiModeWhenAKanjiRunEndsTheText() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePageEncoder enc = encoder(out);
    enc.emit("A漢");
    // Kanji mode is on until finish restores the single-byte baseline.
    assertEquals(true, enc.kanjiMode());
    enc.finish();
    assertEquals(false, enc.kanjiMode());

    final byte[] bytes = out.toByteArray();
    // The last two bytes are FS . emitted by finish.
    assertArrayEquals(new byte[] { 0x1C, 0x2E },
        new byte[] { bytes[bytes.length - 2], bytes[bytes.length - 1] });
  }

  @Test
  void characterNeitherPageNorRomCanEncodeFallsBackToReplacement() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePageEncoder enc = encoder(out);
    enc.emit("한"); // Hangul: not in CP437, not in JIS0208
    enc.finish();

    // No Kanji commands; a single '?' replacement in the current page.
    assertArrayEquals(new byte[] { '?' }, out.toByteArray());
  }

  @Test
  void noMultibyteModeLeavesCjkAsReplacement() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CodePageEncoder enc = new CodePageEncoder(out, CP437, List.of(CP437),
        id -> new byte[] { 0x1B, 0x74, (byte) id }); // no MultibyteMode
    enc.emit("漢");
    enc.finish();

    assertArrayEquals(new byte[] { '?' }, out.toByteArray());
  }
}
