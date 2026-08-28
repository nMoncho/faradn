package io.nmoncho.faradn.printer.escpos.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.BarcodeException;
import io.nmoncho.faradn.document.BarcodeOptions;

public class BarcodeCommandsTest {

  @Test
  void code128PrefixesCodeSetAndLengthPrefixesTheData() {
    byte[] out = BarcodeCommands.encode("code128", "ABC");

    byte[] payload = "{BABC".getBytes(StandardCharsets.US_ASCII);
    byte[] tail = new byte[4 + payload.length];
    tail[0] = 0x1D; // GS
    tail[1] = 0x6B; // k
    tail[2] = 73; // CODE128
    tail[3] = (byte) payload.length; // n
    System.arraycopy(payload, 0, tail, 4, payload.length);

    assertArrayEquals(tail, Arrays.copyOfRange(out, out.length - tail.length, out.length));
  }

  @Test
  void ean13ValidatesItsData() {
    assertThrows(BarcodeException.class, () -> BarcodeCommands.encode("ean13", "12345"));
    assertThrows(BarcodeException.class, () -> BarcodeCommands.encode("ean13", "abcdefghijkl"));
    BarcodeCommands.encode("ean13", "123456789012"); // 12 digits: valid
  }

  @Test
  void unknownSymbologyThrows() {
    assertThrows(BarcodeException.class, () -> BarcodeCommands.encode("bogus", "123"));
  }

  @Test
  void qrEmitsModelSelectionThenPrint() {
    byte[] out = BarcodeCommands.encode("qr", "hi");

    byte[] head = { 0x1D, 0x28, 0x6B, 0x04, 0x00, 49, 65, 50, 0x00 }; // GS ( k, select model 2
    assertArrayEquals(head, Arrays.copyOfRange(out, 0, head.length));

    byte[] tail = { 0x1D, 0x28, 0x6B, 0x03, 0x00, 49, 81, 48 }; // GS ( k, print
    assertArrayEquals(tail, Arrays.copyOfRange(out, out.length - tail.length, out.length));
  }

  @Test
  void oneDimensionalHonoursHeightModuleAndHri() {
    BarcodeOptions options = new BarcodeOptions(50, 4, BarcodeOptions.Hri.ABOVE, BarcodeOptions.QrEc.M);

    byte[] out = BarcodeCommands.encode("code39", "12", options);

    assertContains(out, new byte[] { 0x1D, 0x48, 1 }); // GS H: HRI above
    assertContains(out, new byte[] { 0x1D, 0x68, 50 }); // GS h: height 50
    assertContains(out, new byte[] { 0x1D, 0x77, 4 }); // GS w: module 4
  }

  @Test
  void hriNoneSuppressesHumanReadableText() {
    BarcodeOptions options = new BarcodeOptions(100, 0, BarcodeOptions.Hri.NONE, BarcodeOptions.QrEc.M);

    byte[] out = BarcodeCommands.encode("code39", "12", options);

    assertContains(out, new byte[] { 0x1D, 0x48, 0 }); // GS H: HRI none
  }

  @Test
  void qrHonoursModuleAndErrorCorrection() {
    BarcodeOptions options = new BarcodeOptions(100, 8, BarcodeOptions.Hri.BELOW, BarcodeOptions.QrEc.H);

    byte[] out = BarcodeCommands.encode("qr", "hi", options);

    assertContains(out, new byte[] { 0x1D, 0x28, 0x6B, 0x03, 0x00, 49, 67, 8 }); // module size 8
    assertContains(out, new byte[] { 0x1D, 0x28, 0x6B, 0x03, 0x00, 49, 69, 51 }); // EC level H
  }

  private static void assertContains(byte[] haystack, byte[] needle) {
    outer: for (int i = 0; i + needle.length <= haystack.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return;
    }
    throw new AssertionError("Expected subsequence " + Arrays.toString(needle) + " not found");
  }
}
