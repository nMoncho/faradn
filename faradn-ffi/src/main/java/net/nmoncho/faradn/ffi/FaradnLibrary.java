//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.ffi;

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import net.nmoncho.faradn.Document;
import net.nmoncho.faradn.PrintingException;
import net.nmoncho.faradn.printer.Renderers;
import net.nmoncho.faradn.printer.PrinterProfile;

/**
 * C-callable rendering entry points for the Farad'n shared library. Built with
 * GraalVM native-image {@code --shared}, this exposes the pure, transport-free
 * {@code HTML → ESC/POS bytes} path so any language with a C FFI (C, Rust, Go,
 * Python, …) can render receipts; the host language then writes the bytes to
 * the
 * printer (a TCP socket on port 9100, or the USB device).
 * <p>
 * The boundary is C only: strings are UTF-8 and null-terminated, and returned
 * buffers are allocated with the library's allocator, which the caller must
 * release with {@code faradn_free}. Every call takes an {@code IsolateThread}:
 * create an isolate with {@code graal_create_isolate} once, and attach
 * additional threads with {@code graal_attach_thread}.
 * <p>
 * <strong>Error contract.</strong> {@code faradn_render} returns
 * {@link #FARADN_OK} on success or one of the {@code FARADN_ERR_*} codes on
 * failure. These codes are a stable part of the C ABI. After a failure,
 * {@code faradn_last_error} returns a human-readable message for the calling
 * thread until that thread's next call.
 */
public final class FaradnLibrary {

  /** Success. */
  public static final int FARADN_OK = 0;
  /** An unexpected or unclassified failure. */
  public static final int FARADN_ERR_UNKNOWN = -1;
  /**
   * A required argument was null or invalid (e.g. null HTML or a null
   * out-parameter).
   */
  public static final int FARADN_ERR_INVALID_ARGUMENT = -2;
  /** The named printer profile is not in the capability database. */
  public static final int FARADN_ERR_UNKNOWN_PROFILE = -3;
  /** The HTML could not be parsed or rendered to ESC/POS. */
  public static final int FARADN_ERR_RENDER = -4;
  /** A memory allocation failed. */
  public static final int FARADN_ERR_OUT_OF_MEMORY = -5;

  private static final String DEFAULT_PROFILE = "TM-T88V";

  // Filtered from ${project.version} at build time (see BuildInfo / java-templates).
  private static final String VERSION = BuildInfo.VERSION;

  /**
   * Last error message for the calling thread, or {@code null} when its last call
   * succeeded.
   */
  private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();

  private FaradnLibrary() {
  }

  /**
   * Thrown when a profile name is not found, so the C boundary maps it to its own
   * code.
   */
  static final class UnknownProfileException extends RuntimeException {
    UnknownProfileException(String name) {
      super("Unknown profile: " + name);
    }
  }

  /**
   * Renders {@code html} to ESC/POS bytes for the named profile. On success
   * writes a freshly allocated buffer to {@code outBuffer} and its length to
   * {@code outLength}, and returns {@link #FARADN_OK}; on failure returns one of
   * the {@code FARADN_ERR_*} codes, touches neither out-parameter, and records a
   * message reachable via {@code faradn_last_error}.
   */
  @CEntryPoint(name = "faradn_render")
  static int render(IsolateThread thread, CCharPointer htmlUtf8, CCharPointer profileName,
      CCharPointerPointer outBuffer, CLongPointer outLength) {
    if (outBuffer.isNull() || outLength.isNull()) {
      return fail(FARADN_ERR_INVALID_ARGUMENT, "outBuffer and outLength must not be null");
    }
    try {
      final String html = CTypeConversion.toJavaString(htmlUtf8);
      final String profile = CTypeConversion.toJavaString(profileName);
      final byte[] bytes = renderToBytes(html, profile);

      final CCharPointer buffer = UnmanagedMemory.malloc(bytes.length);
      if (buffer.isNull()) {
        return fail(FARADN_ERR_OUT_OF_MEMORY, "could not allocate " + bytes.length + " bytes");
      }
      for (int i = 0; i < bytes.length; i++) {
        buffer.write(i, bytes[i]);
      }
      outBuffer.write(buffer);
      outLength.write(bytes.length);
      LAST_ERROR.remove();
      return FARADN_OK;
    } catch (Throwable t) {
      return fail(errorCode(t), describe(t));
    }
  }

  /**
   * Returns this thread's last failure message as a freshly allocated,
   * null-terminated UTF-8 C string (release it with {@code faradn_free}), or
   * {@code NULL} when this thread's last call succeeded.
   */
  @CEntryPoint(name = "faradn_last_error")
  static CCharPointer lastError(IsolateThread thread) {
    final String message = LAST_ERROR.get();
    return message == null ? WordFactory.nullPointer() : toCString(message);
  }

  /**
   * Returns the library version as a freshly allocated, null-terminated UTF-8 C
   * string (release it with {@code faradn_free}).
   */
  @CEntryPoint(name = "faradn_version")
  static CCharPointer version(IsolateThread thread) {
    return toCString(VERSION);
  }

  /**
   * Releases a buffer returned by {@code faradn_render},
   * {@code faradn_last_error}
   * or {@code faradn_version}.
   */
  @CEntryPoint(name = "faradn_free")
  static void free(IsolateThread thread, CCharPointer buffer) {
    if (buffer.isNonNull()) {
      UnmanagedMemory.free(buffer);
    }
  }

  // --- Pure, transport-free logic, exercised by the JVM unit tests. ---

  /**
   * Renders HTML to ESC/POS bytes for the named profile (a blank or {@code null}
   * name selects the default, {@value #DEFAULT_PROFILE}). Throws
   * {@link IllegalArgumentException} for null HTML,
   * {@link UnknownProfileException}
   * for an unknown profile, and {@link PrintingException} for a rendering
   * failure.
   *
   * @param html
   *        the HTML to render
   * @param profileName
   *        the printer profile name, or blank/{@code null} for the default
   * @return the rendered printer bytes (the profile's command language)
   */
  static byte[] renderToBytes(String html, String profileName) {
    if (html == null) {
      throw new IllegalArgumentException("html must not be null");
    }
    final String target = (profileName == null || profileName.isBlank()) ? DEFAULT_PROFILE : profileName;
    final PrinterProfile profile = PrinterProfile.load(target)
        .orElseThrow(() -> new UnknownProfileException(target));
    return Renderers.forProfile(profile).render(Document.from(html).blocks(profile.dpi()));
  }

  /** Maps a failure to a stable {@code FARADN_ERR_*} code. */
  static int errorCode(Throwable t) {
    if (t instanceof UnknownProfileException) {
      return FARADN_ERR_UNKNOWN_PROFILE;
    }
    if (t instanceof IllegalArgumentException) {
      return FARADN_ERR_INVALID_ARGUMENT;
    }
    if (t instanceof PrintingException) {
      return FARADN_ERR_RENDER;
    }
    if (t instanceof OutOfMemoryError) {
      return FARADN_ERR_OUT_OF_MEMORY;
    }
    return FARADN_ERR_UNKNOWN;
  }

  private static String describe(Throwable t) {
    final String message = t.getMessage();
    return message == null ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + message;
  }

  private static int fail(int code, String message) {
    LAST_ERROR.set(message);
    return code;
  }

  private static CCharPointer toCString(String value) {
    final byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    final CCharPointer buffer = UnmanagedMemory.malloc(utf8.length + 1);
    if (buffer.isNull()) {
      return WordFactory.nullPointer();
    }
    for (int i = 0; i < utf8.length; i++) {
      buffer.write(i, utf8[i]);
    }
    buffer.write(utf8.length, (byte) 0);
    return buffer;
  }
}
