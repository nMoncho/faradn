package io.nmoncho.faradn.printer.escpos.commands;

import com.typesafe.config.Config;

import io.nmoncho.faradn.printer.escpos.Byteable;
import io.nmoncho.faradn.printer.escpos.Code;
import io.nmoncho.faradn.printer.escpos.ParametricCode;

public class BitImageCommands {

  public static ParametricCode<BitImageMode> SELECT_BIT_IMAGE_MODE = new ParametricCode<>(
      new byte[] { Code.ESC, 0x2A });

  static enum BitImageMode implements Byteable {
    SINGLE_DENSITIY_8BIT(0, 8), DOUBLE_DENSITIY_8BIT(1, 8), SINGLE_DENSITIY_24BIT(32, 8), DOUBLE_DENSITIY_24BIT(33,
        8);

    private final byte[] code;
    private final int verticalBits;

    BitImageMode(int code, int verticalBits) {
      this.code = new byte[] { (byte) code };
      this.verticalBits = verticalBits;
    }

    @Override
    public byte[] getBytes() {
      return code;
    }

    public int verticalBits() {
      return verticalBits;
    }
  }

  static interface ImageColorMode {

    /**
     * Defines if a color should be printed (burned) or not.
     *
     * @param rgbColor
     *        RGB color.
     * @return true if should be printed/burned (black), false otherwise (white).
     */
    boolean shouldPrint(int rgbColor);

    /**
     * Collect a slice of 3 bytes with 24 dots for image printing.
     *
     * @param y
     *        row position of the pixel.
     * @param x
     *        column position of the pixel.
     * @param img
     *        2D array of pixels of the image (RGB, row major order).
     * @return 3 byte array with 24 dots (field set).
     */
    byte[] slice(int x, int y, int[][] img, BitImageMode bitMode);
  }

  static class BlackWhiteColorMode implements ImageColorMode {

    private final int threshold;
    private final double redWeight;
    private final double greenWeight;
    private final double blueWeight;

    public BlackWhiteColorMode() {
      this(127, 0.299, 0.587, 0.114);
    }

    public BlackWhiteColorMode(
        int threshold,
        double redWeight,
        double greenWeight,
        double blueWeight) {
      this.threshold = threshold;
      this.redWeight = redWeight;
      this.greenWeight = greenWeight;
      this.blueWeight = blueWeight;
    }

    @Override
    public boolean shouldPrint(int rgbColor) {
      int a, r, g, b, luminance;

      a = (rgbColor >> 24) & 0xff;
      if (a != 0xff) { // ignore pixels with alpha channel
        return false;
      }

      r = (rgbColor >> 16) & 0xff;
      g = (rgbColor >> 8) & 0xff;
      b = rgbColor & 0xff;

      luminance = (int) (redWeight * r + greenWeight * g + blueWeight * b);

      return luminance < threshold;
    }

    @Override
    public byte[] slice(int x, int y, int[][] img, BitImageMode bitMode) {
      byte[] slices = new byte[] { 0, 0, 0 };

      // set bytes for each vertical slice
      for (int yy = y, i = 0; yy < y + 24 && i < 3; yy += 8, i++) {
        byte slice = 0;

        for (int b = 0; b < 8; b++) {
          int yyy = yy + b;
          if (yyy >= img.length) {
            continue;
          }
          int col = img[yyy][x];
          boolean v = shouldPrint(col);
          slice |= (byte) ((v ? 1 : 0) << (7 - b));
        }

        slices[i] = slice;
      }

      return slices;
    }

    public BlackWhiteColorMode fromConfig(Config config) {
      return new BlackWhiteColorMode(
          config.getInt("threshold"),
          config.getDouble("red-weight"),
          config.getDouble("green-weight"),
          config.getDouble("blue-weight"));
    }
  }
}
