//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.internal.html.HtmlUtil;

public class UtilsTest {

  private static Element withStyle(String style) {
    return Jsoup.parseBodyFragment("<div style=\"" + style + "\">x</div>").body().child(0);
  }

  @Test
  void findsValueWithTrailingSemicolon() {
    assertEquals(Optional.of("center"), HtmlUtil.findStyleValue(withStyle("text-align: center;"), "text-align"));
  }

  @Test
  void findsValueWithoutTrailingSemicolon() {
    assertEquals(Optional.of("center"), HtmlUtil.findStyleValue(withStyle("text-align: center"), "text-align"));
  }

  @Test
  void findsValueAmongMultipleDeclarations() {
    final Element el = withStyle("text-align: center; font-weight: bold; text-decoration: underline");

    assertEquals(Optional.of("center"), HtmlUtil.findStyleValue(el, "text-align"));
    assertEquals(Optional.of("bold"), HtmlUtil.findStyleValue(el, "font-weight"));
    assertEquals(Optional.of("underline"), HtmlUtil.findStyleValue(el, "text-decoration"));
  }

  @Test
  void toleratesWhitespace() {
    assertEquals(Optional.of("bold"), HtmlUtil.findStyleValue(withStyle("  font-weight  :   bold  ; "), "font-weight"));
  }

  @Test
  void propertyNameIsCaseInsensitive() {
    assertEquals(Optional.of("bold"), HtmlUtil.findStyleValue(withStyle("Font-Weight: bold"), "font-weight"));
  }

  @Test
  void matchesWholePropertyNamesOnly() {
    // "text-decoration" must not match a "text-decoration-line" declaration
    // and vice versa
    assertTrue(HtmlUtil.findStyleValue(withStyle("text-decoration-line: underline"), "text-decoration").isEmpty());
    assertTrue(HtmlUtil.findStyleValue(withStyle("text-decoration: underline"), "text-decoration-line").isEmpty());
  }

  @Test
  void absentPropertyIsEmpty() {
    assertTrue(HtmlUtil.findStyleValue(withStyle("font-weight: bold"), "text-align").isEmpty());
  }

  @Test
  void missingStyleAttributeIsEmpty() {
    final Element el = Jsoup.parseBodyFragment("<div>x</div>").body().child(0);

    assertTrue(HtmlUtil.findStyleValue(el, "text-align").isEmpty());
  }

  @Test
  void emptyValueIsEmpty() {
    assertTrue(HtmlUtil.findStyleValue(withStyle("text-align: ;"), "text-align").isEmpty());
  }

  @Test
  void malformedDeclarationsAreSkipped() {
    assertEquals(Optional.of("bold"),
        HtmlUtil.findStyleValue(withStyle("nonsense; : orphan; font-weight: bold"), "font-weight"));
  }

  @Test
  void lengthPxMapsOneToOneToDots() {
    assertEquals(OptionalInt.of(512), Utils.lengthToDots("512px", 180, 0));
    assertEquals(OptionalInt.of(-10), Utils.lengthToDots("-10px", 180, 0)); // caller clamps
    assertEquals(OptionalInt.of(13), Utils.lengthToDots("12.6px", 180, 0)); // rounded
  }

  @Test
  void lengthMmAndCmUseDpi() {
    assertEquals(OptionalInt.of(567), Utils.lengthToDots("80mm", 180, 0)); // 80*180/25.4
    assertEquals(OptionalInt.of(213), Utils.lengthToDots("3cm", 180, 0)); // 30*180/25.4
    assertEquals(OptionalInt.of(7), Utils.lengthToDots("1mm", 180, 0)); // 180/25.4 -> 7
    assertEquals(OptionalInt.of(630), Utils.lengthToDots("80mm", 200, 0)); // dpi matters
  }

  @Test
  void lengthPercentIsFractionOfReference() {
    assertEquals(OptionalInt.of(200), Utils.lengthToDots("50%", 180, 400));
    assertEquals(OptionalInt.of(0), Utils.lengthToDots("50%", 180, 0));
  }

  @Test
  void lengthUnitIsCaseInsensitive() {
    assertEquals(OptionalInt.of(567), Utils.lengthToDots("80MM", 180, 0));
  }

  @Test
  void lengthRejectsUnitlessNullBlankAndUnparseable() {
    assertTrue(Utils.lengthToDots("512", 180, 0).isEmpty()); // no unit
    assertTrue(Utils.lengthToDots(null, 180, 0).isEmpty());
    assertTrue(Utils.lengthToDots("  ", 180, 0).isEmpty());
    assertTrue(Utils.lengthToDots("abcpx", 180, 0).isEmpty());
    assertTrue(Utils.lengthToDots("10em", 180, 0).isEmpty()); // unsupported unit
  }
}
