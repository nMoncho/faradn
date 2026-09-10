//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.printer.EscPosRenderer;
import net.nmoncho.faradn.printer.PrinterProfile;

/**
 * Fuzz and property tests for untrusted input. The library renders attacker-
 * controlled HTML (the {@code faradn serve} path), so malformed markup, bad
 * Base64, and corrupt image bytes must fail as a domain exception, never as a
 * raw {@code ArrayIndexOutOfBounds}, {@code NullPointerException}, an infinite
 * loop, or an out-of-memory crash.
 */
class MalformedInputTest {

  private static final byte[] PNG_MAGIC = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

  private PrinterProfile profile;

  @BeforeEach
  void setUp() {
    Image.policy(ImagePolicy.DATA_URIS_ONLY);
    profile = PrinterProfile.load("TM-T88V").orElseThrow();
  }

  @AfterEach
  void tearDown() {
    Image.policy(ImagePolicy.DATA_URIS_ONLY);
  }

  @Test
  void randomHtmlNeverCrashesTheRenderer() {
    // 500 pseudo-random documents must all render to bytes; the HTML pipeline is
    // lenient (jsoup) and must not throw on garbage text, stray angle brackets,
    // control characters, or unbalanced tags.
    Random random = new Random(20260909L);
    for (int i = 0; i < 500; i++) {
      String html = randomHtml(random);
      try {
        byte[] bytes = new EscPosRenderer(profile).render(Document.from(html).blocks(profile.dpi()));
        assertNotNull(bytes, "render returned null for: " + snippet(html));
      } catch (Throwable t) {
        fail("random HTML crashed the renderer (" + t.getClass().getName() + ") for: " + snippet(html), t);
      }
    }
  }

  @Test
  void deeplyNestedHtmlDoesNotStackOverflow() {
    int depth = 1500;
    String html = "<div>".repeat(depth) + "deep" + "</div>".repeat(depth);
    try {
      byte[] bytes = new EscPosRenderer(profile).render(Document.from(html).blocks(profile.dpi()));
      assertNotNull(bytes);
    } catch (StackOverflowError e) {
      fail("deeply nested HTML overflowed the stack", e);
    } catch (PrintingException e) {
      // A bounded, domain-level rejection is acceptable; a raw crash is not.
    }
  }

  @Test
  void corruptPngBytesAlwaysFailAsPrintingException() {
    // Property: for any byte array, the PNG decoder either returns pixels or
    // throws PrintingException. It must never leak a low-level exception or
    // exhaust memory (dimension and chunk caps guard allocation).
    Random random = new Random(4242L);
    for (int i = 0; i < 400; i++) {
      byte[] data = randomPngLikeBytes(random);
      try {
        RasterImage image = PngDecoder.decode(data);
        assertNotNull(image);
      } catch (PrintingException expected) {
        // sanctioned failure
      } catch (Throwable t) {
        fail("PngDecoder leaked " + t.getClass().getName() + " on malformed input", t);
      }
    }
  }

  @Test
  void emptyAndTruncatedPngInputsAreRejected() {
    assertThrows(PrintingException.class, () -> PngDecoder.decode(new byte[0]));
    assertThrows(PrintingException.class, () -> PngDecoder.decode(PNG_MAGIC)); // magic only, no chunks
    byte[] truncated = new byte[PNG_MAGIC.length + 4];
    System.arraycopy(PNG_MAGIC, 0, truncated, 0, PNG_MAGIC.length);
    assertThrows(PrintingException.class, () -> PngDecoder.decode(truncated));
  }

  @Test
  void malformedBase64DataUriFailsCleanly() {
    // The end-to-end untrusted path: a data: URI whose Base64 is garbage. It must
    // fail as a domain/argument error, not a raw crash, and must not hang.
    String html = "<img src=\"data:image/png;base64,@@@not-base64@@@\">";
    Throwable thrown = assertThrows(RuntimeException.class,
        () -> new EscPosRenderer(profile).render(Document.from(html).blocks(profile.dpi())));
    assertTrue(thrown instanceof PrintingException || thrown instanceof IllegalArgumentException,
        "expected a clean rejection, got " + thrown.getClass().getName());
  }

  @Test
  void validBase64OfNonImageBytesFailsAsPrintingException() {
    // Well-formed Base64 that does not decode to a PNG must be a PrintingException
    // (the image decoder rejects it), not an IllegalArgumentException.
    String garbage = java.util.Base64.getEncoder().encodeToString("this is not a PNG at all".getBytes());
    String html = "<img src=\"data:image/png;base64," + garbage + "\">";
    assertThrows(PrintingException.class,
        () -> new EscPosRenderer(profile).render(Document.from(html).blocks(profile.dpi())));
  }

  // --- generators ---

  private static String randomHtml(Random random) {
    String[] tags = { "p", "b", "i", "u", "h1", "h2", "div", "span", "table", "tr", "td", "br", "hr", "ul", "li",
        "pre", "center" };
    StringBuilder sb = new StringBuilder();
    int parts = random.nextInt(40);
    for (int i = 0; i < parts; i++) {
      switch (random.nextInt(5)) {
        case 0 -> sb.append('<').append(tags[random.nextInt(tags.length)]).append('>');
        case 1 -> sb.append("</").append(tags[random.nextInt(tags.length)]).append('>');
        case 2 -> sb.append(randomText(random));
        case 3 -> sb.append("<>&\"'/\\").append((char) random.nextInt(0x2FFF));
        default -> sb.append(random.nextBoolean() ? "<<<" : ">>>");
      }
    }
    return sb.toString();
  }

  private static String randomText(Random random) {
    int len = random.nextInt(24);
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append((char) random.nextInt(0x2500));
    }
    return sb.toString();
  }

  private static byte[] randomPngLikeBytes(Random random) {
    int length = 1 + random.nextInt(256);
    byte[] data = new byte[length];
    random.nextBytes(data);
    // Half the time, make it look like a PNG so deeper chunk-parsing runs.
    if (random.nextBoolean() && length >= PNG_MAGIC.length) {
      System.arraycopy(PNG_MAGIC, 0, data, 0, PNG_MAGIC.length);
    }
    return data;
  }

  private static String snippet(String html) {
    String oneLine = html.replace('\n', ' ');
    return oneLine.length() > 60 ? oneLine.substring(0, 60) + "..." : oneLine;
  }
}
