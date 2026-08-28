package io.nmoncho.faradn.ffi;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import io.nmoncho.faradn.Document;
import io.nmoncho.faradn.printer.PrinterProfile;
import io.nmoncho.faradn.printer.escpos.EscPosRenderer;

/**
 * C-callable rendering entry points for the Farad'n shared library. Built with
 * GraalVM native-image {@code --shared}, this exposes the pure, transport-free
 * {@code HTML → ESC/POS bytes} path so any language with a C FFI (C, Rust, Go,
 * Python, …) can render receipts; the host language then writes the bytes to
 * the
 * printer (a TCP socket on port 9100, or the USB device).
 * <p>
 * The boundary is C only: strings are UTF-8 and null-terminated, and the
 * rendered bytes are returned in a buffer allocated with the library's
 * allocator, which the caller must release with {@code faradn_free}. Every call
 * takes an {@code IsolateThread}: create an isolate with
 * {@code graal_create_isolate} once, and attach additional threads with
 * {@code graal_attach_thread}.
 */
public final class FaradnLibrary {

  private FaradnLibrary() {
  }

  /**
   * Renders {@code html} to ESC/POS bytes for the named profile. On success
   * writes a freshly allocated buffer to {@code outBuffer} and its length to
   * {@code outLength}, and returns {@code 0}; on failure returns a negative code
   * and touches neither out-parameter.
   */
  @CEntryPoint(name = "faradn_render")
  static int render(IsolateThread thread, CCharPointer htmlUtf8, CCharPointer profileName,
      CCharPointerPointer outBuffer, CLongPointer outLength) {
    try {
      final String html = CTypeConversion.toJavaString(htmlUtf8);
      final PrinterProfile profile = profileFor(CTypeConversion.toJavaString(profileName));
      final byte[] bytes = new EscPosRenderer(profile).render(Document.from(html).blocks());

      final CCharPointer buffer = UnmanagedMemory.malloc(bytes.length);
      for (int i = 0; i < bytes.length; i++) {
        buffer.write(i, bytes[i]);
      }
      outBuffer.write(buffer);
      outLength.write(bytes.length);
      return 0;
    } catch (Throwable t) {
      return -1;
    }
  }

  /** Releases a buffer returned by {@code faradn_render}. */
  @CEntryPoint(name = "faradn_free")
  static void free(IsolateThread thread, CCharPointer buffer) {
    if (buffer.isNonNull()) {
      UnmanagedMemory.free(buffer);
    }
  }

  private static PrinterProfile profileFor(String name) {
    final String target = (name == null || name.isBlank()) ? "TM-T88V" : name;
    return PrinterProfile.load(target)
        .orElseThrow(() -> new IllegalArgumentException("Unknown profile: " + target));
  }
}
