package net.nmoncho.faradn.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.document.LineHeight.Kind;

public class LineHeightTest {

  @Test
  void parsesNormalBlankNullAndGarbageAsNormal() {
    assertEquals(LineHeight.NORMAL, LineHeight.parse("normal"));
    assertEquals(LineHeight.NORMAL, LineHeight.parse("  "));
    assertEquals(LineHeight.NORMAL, LineHeight.parse(null));
    assertEquals(LineHeight.NORMAL, LineHeight.parse("abc"));
    assertEquals(LineHeight.NORMAL, LineHeight.parse("10em")); // unsupported unit
  }

  @Test
  void parsesUnitlessAndPercentAsFactor() {
    assertEquals(new LineHeight(Kind.FACTOR, 1.5), LineHeight.parse("1.5"));
    assertEquals(new LineHeight(Kind.FACTOR, 1.5), LineHeight.parse("150%"));
    assertEquals(new LineHeight(Kind.FACTOR, 2.0), LineHeight.parse("2"));
  }

  @Test
  void parsesAbsoluteLengths() {
    assertEquals(new LineHeight(Kind.PX, 30), LineHeight.parse("30px"));
    assertEquals(new LineHeight(Kind.MM, 5), LineHeight.parse("5mm"));
    assertEquals(new LineHeight(Kind.CM, 1), LineHeight.parse("1CM")); // case-insensitive
  }

  @Test
  void resolvesFactorAgainstCellHeight() {
    assertEquals(OptionalInt.of(36), LineHeight.parse("1.5").resolveDots(24, 180));
    assertEquals(OptionalInt.of(48), LineHeight.parse("200%").resolveDots(24, 180));
  }

  @Test
  void resolvesLengthsToDots() {
    assertEquals(OptionalInt.of(30), LineHeight.parse("30px").resolveDots(24, 180)); // px = dots
    assertEquals(OptionalInt.of(35), LineHeight.parse("5mm").resolveDots(24, 180)); // 5*180/25.4
    assertEquals(OptionalInt.of(71), LineHeight.parse("1cm").resolveDots(24, 180)); // 10*180/25.4
  }

  @Test
  void normalResolvesToEmpty() {
    assertTrue(LineHeight.NORMAL.resolveDots(24, 180).isEmpty());
  }

  @Test
  void clampsToSingleByte() {
    assertEquals(OptionalInt.of(255), LineHeight.parse("999px").resolveDots(24, 180));
    assertEquals(OptionalInt.of(0), LineHeight.parse("0").resolveDots(24, 180));
  }

  @Test
  void rejectsNullKind() {
    assertThrows(IllegalArgumentException.class, () -> new LineHeight(null, 1));
  }
}
