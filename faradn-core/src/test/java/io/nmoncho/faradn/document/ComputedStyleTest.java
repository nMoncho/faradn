package io.nmoncho.faradn.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import io.nmoncho.faradn.document.ComputedStyle.Alignment;

public class ComputedStyleTest {

  private static Element element(String html) {
    return Jsoup.parseBodyFragment(html).body().child(0);
  }

  @Test
  void initialIsPlainLeftAlignedBaseSize() {
    assertFalse(ComputedStyle.INITIAL.bold());
    assertFalse(ComputedStyle.INITIAL.underline());
    assertFalse(ComputedStyle.INITIAL.invert());
    assertEquals(1, ComputedStyle.INITIAL.widthMultiple());
    assertEquals(1, ComputedStyle.INITIAL.heightMultiple());
    assertEquals(Alignment.LEFT, ComputedStyle.INITIAL.alignment());
  }

  @Test
  void boldTags() {
    assertTrue(ComputedStyle.INITIAL.process(element("<b>x</b>")).bold());
    assertTrue(ComputedStyle.INITIAL.process(element("<strong>x</strong>")).bold());
  }

  @Test
  void underlineTag() {
    assertTrue(ComputedStyle.INITIAL.process(element("<u>x</u>")).underline());
  }

  @Test
  void smallTagSelectsFontB() {
    assertEquals(0, ComputedStyle.INITIAL.font());
    assertEquals(1, ComputedStyle.INITIAL.process(element("<small>x</small>")).font());
  }

  @Test
  void fontFamilyCssSelectsFontSlot() {
    assertEquals(1, ComputedStyle.INITIAL.process(element("<span style=\"font-family: font-b\">x</span>")).font());
    assertEquals(0, ComputedStyle.INITIAL.process(element("<span style=\"font-family: font-a\">x</span>")).font());
    assertEquals(2, ComputedStyle.INITIAL.process(element("<span style=\"font-family: font-c\">x</span>")).font());
    // A quoted name inside a font stack still matches.
    assertEquals(1,
        ComputedStyle.INITIAL.process(element("<div style=\"font-family: 'font-b', monospace\">x</div>")).font());
  }

  @Test
  void fontFamilyCssOverridesSmallTag() {
    assertEquals(0,
        ComputedStyle.INITIAL.process(element("<small style=\"font-family: font-a\">x</small>")).font());
  }

  @Test
  void headings() {
    final ComputedStyle h1 = ComputedStyle.INITIAL.process(element("<h1>x</h1>"));
    assertTrue(h1.bold());
    assertEquals(2, h1.widthMultiple());
    assertEquals(2, h1.heightMultiple());

    final ComputedStyle h2 = ComputedStyle.INITIAL.process(element("<h2>x</h2>"));
    assertTrue(h2.bold());
    assertEquals(1, h2.widthMultiple());
    assertEquals(2, h2.heightMultiple());

    final ComputedStyle h3 = ComputedStyle.INITIAL.process(element("<h3>x</h3>"));
    assertTrue(h3.bold());
    assertEquals(1, h3.widthMultiple());
    assertEquals(1, h3.heightMultiple());
  }

  @Test
  void centerTag() {
    assertEquals(Alignment.CENTER, ComputedStyle.INITIAL.process(element("<center>x</center>")).alignment());
  }

  @Test
  void italicTagsHaveNoEffect() {
    // ESC/POS has no italic: <em>/<i> must not change the style, and an
    // unchanged style must be the same instance
    assertSame(ComputedStyle.INITIAL, ComputedStyle.INITIAL.process(element("<em>x</em>")));
    assertSame(ComputedStyle.INITIAL, ComputedStyle.INITIAL.process(element("<i>x</i>")));
  }

  @Test
  void cssFontWeight() {
    assertTrue(ComputedStyle.INITIAL.process(element("<span style=\"font-weight: bold;\">x</span>")).bold());
    assertTrue(ComputedStyle.INITIAL.process(element("<span style=\"font-weight: 700;\">x</span>")).bold());
    assertFalse(ComputedStyle.INITIAL.process(element("<span style=\"font-weight: normal;\">x</span>")).bold());
  }

  @Test
  void cssOverridesTagDefault() {
    assertFalse(ComputedStyle.INITIAL.process(element("<b style=\"font-weight: normal;\">x</b>")).bold());
  }

  @Test
  void cssTextDecoration() {
    assertTrue(ComputedStyle.INITIAL.process(element("<span style=\"text-decoration: underline;\">x</span>"))
        .underline());

    final ComputedStyle underlined = new ComputedStyle(false, true, 1, 1, Alignment.LEFT, false);
    assertFalse(underlined.process(element("<span style=\"text-decoration: none;\">x</span>")).underline());
  }

  @Test
  void cssTextAlignWithoutTrailingSemicolon() {
    // Regression: the old parser required a ';' after every declaration
    assertEquals(Alignment.CENTER,
        ComputedStyle.INITIAL.process(element("<div style=\"text-align: center\">x</div>")).alignment());
    assertEquals(Alignment.RIGHT,
        ComputedStyle.INITIAL.process(element("<div style=\"text-align: right\">x</div>")).alignment());
  }

  @Test
  void inheritsFromCurrentStyle() {
    final ComputedStyle bold = ComputedStyle.INITIAL.process(element("<b>x</b>"));
    final ComputedStyle boldUnderlined = bold.process(element("<u>x</u>"));

    assertTrue(boldUnderlined.bold());
    assertTrue(boldUnderlined.underline());
  }

  @Test
  void sizeMultiplesAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new ComputedStyle(false, false, 0, 1, Alignment.LEFT, false));
    assertThrows(IllegalArgumentException.class, () -> new ComputedStyle(false, false, 1, 9, Alignment.LEFT, false));
  }
}
