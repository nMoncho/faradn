package io.nmoncho.faradn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A small, dependency-free PNG decoder producing {@link RasterImage} pixels.
 * <p>
 * It exists so the native binary can rasterize images without
 * {@code java.awt}/ImageIO, which rely on a native library GraalVM native-image
 * cannot supply. Supports non-interlaced PNGs in every colour type, grayscale,
 * RGB, palette, grayscale+alpha, RGBA, at bit depths 1-8 (16-bit channels are
 * reduced to 8). Interlaced PNGs are rejected.
 */
public final class PngDecoder {

  private static final long SIGNATURE = 0x89504E470D0A1A0AL;
  private static final int IHDR = 0x49484452;
  private static final int PLTE = 0x504C5445;
  private static final int TRNS = 0x74524E53;
  private static final int IDAT = 0x49444154;
  private static final int IEND = 0x49454E44;

  private PngDecoder() {
  }

  /** Whether {@code data} starts with the PNG signature. */
  public static boolean isPng(byte[] data) {
    if (data.length < 8) {
      return false;
    }
    long signature = 0;
    for (int i = 0; i < 8; i++) {
      signature = (signature << 8) | (data[i] & 0xFFL);
    }
    return signature == SIGNATURE;
  }

  /** Decodes a PNG into ARGB pixels. */
  public static RasterImage decode(byte[] data) {
    try {
      return doDecode(data);
    } catch (IOException | DataFormatException | RuntimeException e) {
      throw new PrintingException("Failed to decode PNG image", e);
    }
  }

  private static RasterImage doDecode(byte[] data) throws IOException, DataFormatException {
    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
    in.skipBytes(8); // signature

    int width = 0;
    int height = 0;
    int bitDepth = 0;
    int colorType = 0;
    int interlace = 0;
    byte[] palette = null;
    byte[] transparency = null;
    final ByteArrayOutputStream idat = new ByteArrayOutputStream();

    boolean done = false;
    while (!done) {
      final int length = in.readInt();
      final int type = in.readInt();
      switch (type) {
        case IHDR -> {
          width = in.readInt();
          height = in.readInt();
          bitDepth = in.readUnsignedByte();
          colorType = in.readUnsignedByte();
          in.readUnsignedByte(); // compression method
          in.readUnsignedByte(); // filter method
          interlace = in.readUnsignedByte();
        }
        case PLTE -> {
          palette = new byte[length];
          in.readFully(palette);
        }
        case TRNS -> {
          transparency = new byte[length];
          in.readFully(transparency);
        }
        case IDAT -> {
          final byte[] chunk = new byte[length];
          in.readFully(chunk);
          idat.write(chunk);
        }
        case IEND -> done = true;
        default -> in.skipBytes(length);
      }
      in.readInt(); // CRC (unchecked)
    }

    if (interlace != 0) {
      throw new PrintingException("Interlaced PNG images are not supported");
    }

    final byte[] raw = inflate(idat.toByteArray());
    return toRaster(raw, width, height, bitDepth, colorType, palette, transparency);
  }

  private static byte[] inflate(byte[] compressed) throws DataFormatException {
    final Inflater inflater = new Inflater();
    inflater.setInput(compressed);
    final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, compressed.length * 4));
    final byte[] buffer = new byte[8192];
    while (!inflater.finished()) {
      final int n = inflater.inflate(buffer);
      if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
        break;
      }
      out.write(buffer, 0, n);
    }
    inflater.end();
    return out.toByteArray();
  }

  private static RasterImage toRaster(byte[] raw, int width, int height, int bitDepth, int colorType,
      byte[] palette, byte[] transparency) {
    final int channels = channels(colorType);
    final int bitsPerPixel = channels * bitDepth;
    final int bytesPerPixel = Math.max(1, bitsPerPixel / 8);
    final int stride = (width * bitsPerPixel + 7) / 8;

    byte[] previous = new byte[stride];
    byte[] current = new byte[stride];
    final int[] argb = new int[width * height];
    int pos = 0;

    for (int y = 0; y < height; y++) {
      final int filter = raw[pos++] & 0xFF;
      System.arraycopy(raw, pos, current, 0, stride);
      pos += stride;
      unfilter(filter, current, previous, bytesPerPixel);
      readScanline(current, y, width, bitDepth, colorType, palette, transparency, argb);

      final byte[] swap = previous;
      previous = current;
      current = swap;
    }
    return new RasterImage(width, height, argb);
  }

  private static void unfilter(int filter, byte[] cur, byte[] prev, int bpp) {
    switch (filter) {
      case 0 -> {
        // None
      }
      case 1 -> {
        for (int i = bpp; i < cur.length; i++) {
          cur[i] = (byte) (cur[i] + cur[i - bpp]);
        }
      }
      case 2 -> {
        for (int i = 0; i < cur.length; i++) {
          cur[i] = (byte) (cur[i] + prev[i]);
        }
      }
      case 3 -> {
        for (int i = 0; i < cur.length; i++) {
          final int a = i >= bpp ? cur[i - bpp] & 0xFF : 0;
          final int b = prev[i] & 0xFF;
          cur[i] = (byte) (cur[i] + ((a + b) >> 1));
        }
      }
      case 4 -> {
        for (int i = 0; i < cur.length; i++) {
          final int a = i >= bpp ? cur[i - bpp] & 0xFF : 0;
          final int b = prev[i] & 0xFF;
          final int c = i >= bpp ? prev[i - bpp] & 0xFF : 0;
          cur[i] = (byte) (cur[i] + paeth(a, b, c));
        }
      }
      default -> throw new PrintingException("Unsupported PNG filter type: " + filter);
    }
  }

  private static int paeth(int a, int b, int c) {
    final int p = a + b - c;
    final int pa = Math.abs(p - a);
    final int pb = Math.abs(p - b);
    final int pc = Math.abs(p - c);
    if (pa <= pb && pa <= pc) {
      return a;
    }
    return pb <= pc ? b : c;
  }

  private static void readScanline(byte[] line, int y, int width, int bitDepth, int colorType,
      byte[] palette, byte[] transparency, int[] argb) {
    final int base = y * width;
    final int step = Math.max(1, bitDepth / 8); // bytes per channel sample (1 for <=8 bit, 2 for 16 bit)
    for (int x = 0; x < width; x++) {
      int a = 255;
      int r;
      int g;
      int b;
      switch (colorType) {
        case 0 -> {
          final int gray = scaleTo8(sample(line, x, bitDepth), bitDepth);
          r = gray;
          g = gray;
          b = gray;
        }
        case 2 -> {
          final int off = x * step * 3;
          r = line[off] & 0xFF;
          g = line[off + step] & 0xFF;
          b = line[off + 2 * step] & 0xFF;
        }
        case 3 -> {
          final int idx = sample(line, x, bitDepth);
          r = palette[idx * 3] & 0xFF;
          g = palette[idx * 3 + 1] & 0xFF;
          b = palette[idx * 3 + 2] & 0xFF;
          if (transparency != null && idx < transparency.length) {
            a = transparency[idx] & 0xFF;
          }
        }
        case 4 -> {
          final int off = x * step * 2;
          final int gray = line[off] & 0xFF;
          a = line[off + step] & 0xFF;
          r = gray;
          g = gray;
          b = gray;
        }
        case 6 -> {
          final int off = x * step * 4;
          r = line[off] & 0xFF;
          g = line[off + step] & 0xFF;
          b = line[off + 2 * step] & 0xFF;
          a = line[off + 3 * step] & 0xFF;
        }
        default -> throw new PrintingException("Unsupported PNG color type: " + colorType);
      }
      argb[base + x] = (a << 24) | (r << 16) | (g << 8) | b;
    }
  }

  /**
   * Reads a single-channel sample (grayscale value or palette index) at pixel
   * {@code x}.
   */
  private static int sample(byte[] line, int x, int bitDepth) {
    if (bitDepth == 8) {
      return line[x] & 0xFF;
    }
    if (bitDepth == 16) {
      return line[x * 2] & 0xFF; // high byte
    }
    final int perByte = 8 / bitDepth;
    final int byteIndex = x / perByte;
    final int shift = (perByte - 1 - (x % perByte)) * bitDepth;
    final int mask = (1 << bitDepth) - 1;
    return (line[byteIndex] >> shift) & mask;
  }

  private static int scaleTo8(int value, int bitDepth) {
    if (bitDepth >= 8) {
      return value;
    }
    return value * 255 / ((1 << bitDepth) - 1);
  }

  private static int channels(int colorType) {
    return switch (colorType) {
      case 0 -> 1;
      case 2 -> 3;
      case 3 -> 1;
      case 4 -> 2;
      case 6 -> 4;
      default -> throw new PrintingException("Unsupported PNG color type: " + colorType);
    };
  }
}
