package net.nmoncho.faradn.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;

public class BlockValidationTest {

  private static final TextRun RUN = new TextRun("text", ComputedStyle.INITIAL);

  @Test
  void textRunRejectsMissingText() {
    assertThrows(IllegalArgumentException.class, () -> new TextRun(null, ComputedStyle.INITIAL));
    assertThrows(IllegalArgumentException.class, () -> new TextRun("", ComputedStyle.INITIAL));
  }

  @Test
  void textRunRejectsMissingStyle() {
    assertThrows(IllegalArgumentException.class, () -> new TextRun("text", null));
  }

  @Test
  void paragraphRejectsMissingRuns() {
    assertThrows(IllegalArgumentException.class, () -> new Paragraph(null, Alignment.LEFT));
    assertThrows(IllegalArgumentException.class, () -> new Paragraph(List.of(), Alignment.LEFT));
  }

  @Test
  void paragraphRejectsMissingAlignment() {
    assertThrows(IllegalArgumentException.class, () -> new Paragraph(List.of(RUN), null));
  }

  @Test
  void paragraphCopiesItsRuns() {
    final List<TextRun> runs = new ArrayList<>();
    runs.add(RUN);

    final Paragraph p = new Paragraph(runs, Alignment.LEFT);
    runs.add(new TextRun("later", ComputedStyle.INITIAL));

    assertEquals(1, p.runs().size());
    assertThrows(UnsupportedOperationException.class, () -> p.runs().add(RUN));
  }

  @Test
  void imageBlockRejectsMissingParts() {
    final Image image = Image.fromUrl("http://example.com/logo.png");

    assertThrows(IllegalArgumentException.class, () -> new ImageBlock(null, Alignment.LEFT));
    assertThrows(IllegalArgumentException.class, () -> new ImageBlock(image, null));
  }

  @Test
  void barcodeRejectsMissingData() {
    assertThrows(IllegalArgumentException.class, () -> new Barcode(null, "code128", Alignment.LEFT));
    assertThrows(IllegalArgumentException.class, () -> new Barcode("", "code128", Alignment.LEFT));
  }

  @Test
  void barcodeRejectsMissingAlignment() {
    assertThrows(IllegalArgumentException.class, () -> new Barcode("12345678", "code128", null));
  }

  @Test
  void barcodeDefaultsItsSymbology() {
    assertEquals(Barcode.DEFAULT_SYMBOLOGY, new Barcode("12345678", null, Alignment.LEFT).symbology());
    assertEquals(Barcode.DEFAULT_SYMBOLOGY, new Barcode("12345678", "", Alignment.LEFT).symbology());
    assertEquals("upc-a", new Barcode("12345678", "upc-a", Alignment.LEFT).symbology());
  }

  @Test
  void feedRejectsNonPositiveLines() {
    assertThrows(IllegalArgumentException.class, () -> new Feed(0));
    assertThrows(IllegalArgumentException.class, () -> new Feed(-1));
    assertEquals(3, new Feed(3).lines());
  }

  @Test
  void cutCarriesItsMode() {
    assertTrue(new Cut(true).partial());
    assertFalse(new Cut(false).partial());
  }
}
