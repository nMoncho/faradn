package net.nmoncho.faradn.ffi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.PrintingException;
import net.nmoncho.faradn.ffi.FaradnLibrary.UnknownProfileException;

/**
 * JVM tests for the transport-free logic behind the C entry points. The
 * {@code @CEntryPoint} methods themselves need a native isolate, but the render
 * and error-classification logic is plain Java and is verified here.
 */
class FaradnLibraryTest {

  @Test
  void rendersValidHtmlToEscPosBytes() {
    final byte[] bytes = FaradnLibrary.renderToBytes("<h1>Hi</h1>", "TM-T88V");

    assertTrue(bytes.length > 0);
    // The renderer emits ESC @ (initialize) first.
    assertEquals(0x1B, bytes[0] & 0xFF);
    assertEquals(0x40, bytes[1] & 0xFF);
  }

  @Test
  void blankOrNullProfileUsesTheDefault() {
    final byte[] expected = FaradnLibrary.renderToBytes("<p>x</p>", "TM-T88V");
    assertArrayEquals(expected, FaradnLibrary.renderToBytes("<p>x</p>", ""));
    assertArrayEquals(expected, FaradnLibrary.renderToBytes("<p>x</p>", null));
  }

  @Test
  void nullHtmlIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> FaradnLibrary.renderToBytes(null, "TM-T88V"));
  }

  @Test
  void unknownProfileIsRejected() {
    assertThrows(UnknownProfileException.class, () -> FaradnLibrary.renderToBytes("<p>x</p>", "NOPE-9000"));
  }

  @Test
  void errorCodeMapsEachFailureClassToAStableCode() {
    assertEquals(FaradnLibrary.FARADN_ERR_UNKNOWN_PROFILE, FaradnLibrary.errorCode(new UnknownProfileException("x")));
    assertEquals(FaradnLibrary.FARADN_ERR_INVALID_ARGUMENT, FaradnLibrary.errorCode(new IllegalArgumentException("x")));
    assertEquals(FaradnLibrary.FARADN_ERR_RENDER, FaradnLibrary.errorCode(new PrintingException("x")));
    assertEquals(FaradnLibrary.FARADN_ERR_OUT_OF_MEMORY, FaradnLibrary.errorCode(new OutOfMemoryError()));
    assertEquals(FaradnLibrary.FARADN_ERR_UNKNOWN, FaradnLibrary.errorCode(new RuntimeException("x")));
  }

  @Test
  void errorCodesHaveDistinctStableValues() {
    // These values are a published C ABI: they must not shift between releases.
    assertEquals(0, FaradnLibrary.FARADN_OK);
    assertEquals(-1, FaradnLibrary.FARADN_ERR_UNKNOWN);
    assertEquals(-2, FaradnLibrary.FARADN_ERR_INVALID_ARGUMENT);
    assertEquals(-3, FaradnLibrary.FARADN_ERR_UNKNOWN_PROFILE);
    assertEquals(-4, FaradnLibrary.FARADN_ERR_RENDER);
    assertEquals(-5, FaradnLibrary.FARADN_ERR_OUT_OF_MEMORY);
  }

  @Test
  void versionIsFilteredFromTheBuild() {
    assertFalse(BuildInfo.VERSION.isBlank());
    assertFalse(BuildInfo.VERSION.contains("$"), "the ${project.version} placeholder was not filtered");
  }

  @Test
  void unknownProfileFromTheRenderPathClassifiesAsUnknownProfile() {
    try {
      FaradnLibrary.renderToBytes("<p>x</p>", "NOPE-9000");
      throw new AssertionError("expected an exception");
    } catch (Throwable t) {
      assertEquals(FaradnLibrary.FARADN_ERR_UNKNOWN_PROFILE, FaradnLibrary.errorCode(t));
    }
  }
}
