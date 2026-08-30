package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

public class UtilsTest {

  private static Element withStyle(String style) {
    return Jsoup.parseBodyFragment("<div style=\"" + style + "\">x</div>").body().child(0);
  }

  @Test
  void findsValueWithTrailingSemicolon() {
    assertEquals(Optional.of("center"), Utils.findStyleValue(withStyle("text-align: center;"), "text-align"));
  }

  @Test
  void findsValueWithoutTrailingSemicolon() {
    assertEquals(Optional.of("center"), Utils.findStyleValue(withStyle("text-align: center"), "text-align"));
  }

  @Test
  void findsValueAmongMultipleDeclarations() {
    final Element el = withStyle("text-align: center; font-weight: bold; text-decoration: underline");

    assertEquals(Optional.of("center"), Utils.findStyleValue(el, "text-align"));
    assertEquals(Optional.of("bold"), Utils.findStyleValue(el, "font-weight"));
    assertEquals(Optional.of("underline"), Utils.findStyleValue(el, "text-decoration"));
  }

  @Test
  void toleratesWhitespace() {
    assertEquals(Optional.of("bold"), Utils.findStyleValue(withStyle("  font-weight  :   bold  ; "), "font-weight"));
  }

  @Test
  void propertyNameIsCaseInsensitive() {
    assertEquals(Optional.of("bold"), Utils.findStyleValue(withStyle("Font-Weight: bold"), "font-weight"));
  }

  @Test
  void matchesWholePropertyNamesOnly() {
    // "text-decoration" must not match a "text-decoration-line" declaration
    // and vice versa
    assertTrue(Utils.findStyleValue(withStyle("text-decoration-line: underline"), "text-decoration").isEmpty());
    assertTrue(Utils.findStyleValue(withStyle("text-decoration: underline"), "text-decoration-line").isEmpty());
  }

  @Test
  void absentPropertyIsEmpty() {
    assertTrue(Utils.findStyleValue(withStyle("font-weight: bold"), "text-align").isEmpty());
  }

  @Test
  void missingStyleAttributeIsEmpty() {
    final Element el = Jsoup.parseBodyFragment("<div>x</div>").body().child(0);

    assertTrue(Utils.findStyleValue(el, "text-align").isEmpty());
  }

  @Test
  void emptyValueIsEmpty() {
    assertTrue(Utils.findStyleValue(withStyle("text-align: ;"), "text-align").isEmpty());
  }

  @Test
  void malformedDeclarationsAreSkipped() {
    assertEquals(Optional.of("bold"),
        Utils.findStyleValue(withStyle("nonsense; : orphan; font-weight: bold"), "font-weight"));
  }
}
