package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class PngDecoderTest {

  @Test
  void rejectsOversizedDimensionsBeforeAllocating() {
    // A PNG whose IHDR claims a 100000x100000 image (10 billion pixels) must be
    // rejected up front, not allocated.
    assertThrows(PrintingException.class, () -> PngDecoder.decode(pngWithDimensions(100_000, 100_000)));
  }

  @Test
  void rejectsOutOfBoundsChunkLength() {
    final byte[] png = pngWithDimensions(1, 1);
    ByteBuffer.wrap(png).putInt(8, Integer.MAX_VALUE); // corrupt the IHDR length field
    assertThrows(PrintingException.class, () -> PngDecoder.decode(png));
  }

  private static byte[] pngWithDimensions(int width, int height) {
    final ByteBuffer buf = ByteBuffer.allocate(8 + 4 + 4 + 13 + 4);
    buf.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' }); // signature
    buf.putInt(13); // IHDR data length
    buf.putInt(0x49484452); // "IHDR"
    buf.putInt(width);
    buf.putInt(height);
    buf.put((byte) 8); // bit depth
    buf.put((byte) 6); // color type (RGBA)
    buf.put((byte) 0); // compression
    buf.put((byte) 0); // filter
    buf.put((byte) 0); // interlace
    buf.putInt(0); // CRC (unchecked by the decoder)
    return buf.array();
  }
}
