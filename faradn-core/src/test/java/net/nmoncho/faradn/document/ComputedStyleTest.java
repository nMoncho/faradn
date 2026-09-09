package net.nmoncho.faradn.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.internal.html.StyleResolver;

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
    assertTrue(StyleResolver.resolve(ComputedStyle.INITIAL, element("<b>x</b>")).bold());
    assertTrue(StyleResolver.resolve(ComputedStyle.INITIAL, element("<strong>x</strong>")).bold());
  }

  @Test
  void underlineTag() {
    assertTrue(StyleResolver.resolve(ComputedStyle.INITIAL, element("<u>x</u>")).underline());
  }

  @Test
  void smallTagSelectsFontB() {
    assertEquals(0, ComputedStyle.INITIAL.font());
    assertEquals(1, StyleResolver.resolve(ComputedStyle.INITIAL, element("<small>x</small>")).font());
  }

  @Test
  void fontFamilyCssSelectsFontSlot() {
    assertEquals(1,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-family: font-b\">x</span>")).font());
    assertEquals(0,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-family: font-a\">x</span>")).font());
    assertEquals(2,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-family: font-c\">x</span>")).font());
    // A quoted name inside a font stack still matches.
    assertEquals(1,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<div style=\"font-family: 'font-b', monospace\">x</div>"))
            .font());
  }

  @Test
  void fontFamilyCssOverridesSmallTag() {
    assertEquals(0,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<small style=\"font-family: font-a\">x</small>")).font());
  }

  @Test
  void headings() {
    final ComputedStyle h1 = StyleResolver.resolve(ComputedStyle.INITIAL, element("<h1>x</h1>"));
    assertTrue(h1.bold());
    assertEquals(2, h1.widthMultiple());
    assertEquals(2, h1.heightMultiple());

    final ComputedStyle h2 = StyleResolver.resolve(ComputedStyle.INITIAL, element("<h2>x</h2>"));
    assertTrue(h2.bold());
    assertEquals(1, h2.widthMultiple());
    assertEquals(2, h2.heightMultiple());

    final ComputedStyle h3 = StyleResolver.resolve(ComputedStyle.INITIAL, element("<h3>x</h3>"));
    assertTrue(h3.bold());
    assertEquals(1, h3.widthMultiple());
    assertEquals(1, h3.heightMultiple());
  }

  @Test
  void centerTag() {
    assertEquals(Alignment.CENTER,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<center>x</center>")).alignment());
  }

  @Test
  void italicTags() {
    assertTrue(StyleResolver.resolve(ComputedStyle.INITIAL, element("<em>x</em>")).italic());
    assertTrue(StyleResolver.resolve(ComputedStyle.INITIAL, element("<i>x</i>")).italic());
  }

  @Test
  void fontStyleCssSelectsItalic() {
    assertTrue(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-style: italic\">x</span>")).italic());
    assertTrue(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-style: oblique\">x</span>")).italic());
    // font-style: normal switches italic off again inside an <em>
    assertFalse(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<em style=\"font-style: normal\">x</em>")).italic());
  }

  @Test
  void darkBackgroundTurnsInvertOn() {
    assertTrue(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<div style=\"background: black\">x</div>")).invert());
    assertTrue(StyleResolver.resolve(ComputedStyle.INITIAL, element("<div style=\"background-color: #000\">x</div>"))
        .invert());
    // any non-white colour inks the line on a monochrome printer
    assertTrue(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<div style=\"background: #c00\">x</div>")).invert());
  }

  @Test
  void whiteOrTransparentBackgroundTurnsInvertOff() {
    final ComputedStyle inverted = StyleResolver.resolve(ComputedStyle.INITIAL,
        element("<div style=\"background: black\">x</div>"));
    assertTrue(inverted.invert());
    // an explicit white/transparent background inside a dark one turns it back off
    assertFalse(StyleResolver.resolve(inverted, element("<span style=\"background: white\">x</span>")).invert());
    assertFalse(StyleResolver.resolve(inverted, element("<span style=\"background: transparent\">x</span>")).invert());
  }

  @Test
  void unstyledElementReturnsTheSameInstance() {
    // An element that changes nothing returns this, so identity detects transitions.
    assertSame(ComputedStyle.INITIAL, StyleResolver.resolve(ComputedStyle.INITIAL, element("<span>x</span>")));
  }

  @Test
  void cssFontWeight() {
    assertTrue(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-weight: bold;\">x</span>")).bold());
    assertTrue(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-weight: 700;\">x</span>")).bold());
    assertFalse(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-weight: normal;\">x</span>")).bold());
  }

  @Test
  void cssOverridesTagDefault() {
    assertFalse(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<b style=\"font-weight: normal;\">x</b>")).bold());
  }

  @Test
  void cssTextDecoration() {
    assertTrue(
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"text-decoration: underline;\">x</span>"))
            .underline());

    final ComputedStyle underlined = new ComputedStyle(false, true, 1, 1, Alignment.LEFT, false);
    assertFalse(
        StyleResolver.resolve(underlined, element("<span style=\"text-decoration: none;\">x</span>")).underline());
  }

  @Test
  void cssTextAlignWithoutTrailingSemicolon() {
    // Regression: the old parser required a ';' after every declaration
    assertEquals(Alignment.CENTER,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<div style=\"text-align: center\">x</div>")).alignment());
    assertEquals(Alignment.RIGHT,
        StyleResolver.resolve(ComputedStyle.INITIAL, element("<div style=\"text-align: right\">x</div>")).alignment());
  }

  @Test
  void inheritsFromCurrentStyle() {
    final ComputedStyle bold = StyleResolver.resolve(ComputedStyle.INITIAL, element("<b>x</b>"));
    final ComputedStyle boldUnderlined = StyleResolver.resolve(bold, element("<u>x</u>"));

    assertTrue(boldUnderlined.bold());
    assertTrue(boldUnderlined.underline());
  }

  @Test
  void sizeMultiplesAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new ComputedStyle(false, false, 0, 1, Alignment.LEFT, false));
    assertThrows(IllegalArgumentException.class, () -> new ComputedStyle(false, false, 1, 9, Alignment.LEFT, false));
  }

  private static ComputedStyle sized(String css) {
    return StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font-size: " + css + "\">x</span>"));
  }

  @Test
  void fontSizeRelativeUnitsScaleBothAxes() {
    for (String css : new String[] { "200%", "2em", "2rem", "32px" }) {
      final ComputedStyle s = sized(css);
      assertEquals(2, s.widthMultiple(), css);
      assertEquals(2, s.heightMultiple(), css);
    }
  }

  @Test
  void fontSizeKeywordsMap() {
    assertEquals(1, sized("medium").widthMultiple());
    assertEquals(1, sized("small").widthMultiple()); // can't go below 1x
    assertEquals(2, sized("large").widthMultiple());
    assertEquals(3, sized("x-large").widthMultiple());
    assertEquals(4, sized("xx-large").heightMultiple());
  }

  @Test
  void fontSizeClampsAndRounds() {
    assertEquals(1, sized("8px").widthMultiple()); // 0.5x -> clamps to 1
    assertEquals(8, sized("999%").widthMultiple()); // clamps to 8x
    assertEquals(3, sized("250%").widthMultiple()); // rounds 2.5 -> 3
  }

  @Test
  void fontSizeOverridesHeadingSize() {
    // <h2> is normally 1x wide, 2x tall; an explicit font-size wins on both axes.
    final ComputedStyle s = StyleResolver.resolve(ComputedStyle.INITIAL,
        element("<h2 style=\"font-size: 300%\">x</h2>"));
    assertEquals(3, s.widthMultiple());
    assertEquals(3, s.heightMultiple());
    assertTrue(s.bold()); // heading bold still applies
  }

  @Test
  void fontSizeUnparseableIsIgnored() {
    final ComputedStyle s = sized("gigantic");
    assertEquals(1, s.widthMultiple());
    assertEquals(1, s.heightMultiple());
  }

  private static ComputedStyle font(String css) {
    return StyleResolver.resolve(ComputedStyle.INITIAL, element("<span style=\"font: " + css + "\">x</span>"));
  }

  @Test
  void fontShorthandSizeAndFamily() {
    final ComputedStyle s = font("2em font-b");
    assertEquals(2, s.widthMultiple());
    assertEquals(2, s.heightMultiple());
    assertEquals(1, s.font()); // font-b
    assertFalse(s.bold());
    assertFalse(s.italic());
    assertEquals(LineHeight.NORMAL, s.lineHeight());
  }

  @Test
  void fontShorthandWithStyleWeightAndLineHeight() {
    final ComputedStyle s = font("italic bold 200%/1.5 font-c");
    assertTrue(s.italic());
    assertTrue(s.bold());
    assertEquals(2, s.widthMultiple());
    assertEquals(2, s.font()); // font-c
    assertEquals(new LineHeight(LineHeight.Kind.FACTOR, 1.5), s.lineHeight());
  }

  @Test
  void fontShorthandNumericWeight() {
    assertTrue(font("700 2em font-a").bold());
    assertFalse(font("400 2em font-a").bold());
  }

  @Test
  void fontShorthandGenericFamilyResetsToFontA() {
    final ComputedStyle s = font("12px monospace"); // 12/16 -> 1x; monospace has no slot -> Font A
    assertEquals(1, s.widthMultiple());
    assertEquals(0, s.font());
  }

  @Test
  void fontShorthandResetsOmittedComponents() {
    // The shorthand omits weight, so it resets <b>'s bold to normal (per CSS).
    assertFalse(StyleResolver.resolve(ComputedStyle.INITIAL, element("<b style=\"font: 2em font-a\">x</b>")).bold());
  }

  @Test
  void explicitLonghandOverridesFontShorthand() {
    assertTrue(font("2em font-a; font-weight: bold").bold());
  }

  @Test
  void fontShorthandInvalidIsIgnored() {
    assertEquals(1, font("2em").widthMultiple()); // no family
    assertEquals(1, font("menu").widthMultiple()); // system keyword
    assertEquals(1, font("gibberish family").widthMultiple()); // no size
  }
}
