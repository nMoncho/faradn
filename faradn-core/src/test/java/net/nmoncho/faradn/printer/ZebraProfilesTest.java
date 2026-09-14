//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.document.Paragraph;
import net.nmoncho.faradn.document.TextRun;
import net.nmoncho.faradn.printer.epl.EplRenderer;
import net.nmoncho.faradn.printer.zpl.ZplRenderer;

class ZebraProfilesTest {

  @Test
  void zd421Zpl203GeometryLanguageAndMedia() {
    final PrinterProfile p = ZebraProfiles.zd421Zpl203();

    assertEquals("Zebra ZD421 (ZPL, 203dpi)", p.name());
    assertEquals(203, p.dpi());
    assertEquals(832, p.dotsPerLine()); // User Guide ^PW max at 203 dpi
    assertEquals(42, p.columns()); // base 1x font metric (~20-dot char cell)
    assertEquals(PrinterLanguage.ZPL, p.language());
    assertFalse(p.supportsCut()); // no cutter on the base model
    assertEquals(MediaType.DIRECT_THERMAL, p.mediaType());
    assertEquals(MediaTracking.GAP, p.mediaTracking());
    assertTrue(p.supportsBarcodes()); // native on a label printer
    assertTrue(p.supportsImages());
  }

  @Test
  void zd421Zpl300Geometry() {
    final PrinterProfile p = ZebraProfiles.zd421Zpl300();

    assertEquals(300, p.dpi());
    assertEquals(1280, p.dotsPerLine()); // User Guide ^PW max at 300 dpi
    assertEquals(64, p.columns());
    assertEquals(PrinterLanguage.ZPL, p.language());
  }

  @Test
  void selectsTheZplBackend() {
    assertInstanceOf(ZplRenderer.class, Renderers.forProfile(ZebraProfiles.zd421Zpl203()));
  }

  @Test
  void mediaDefaultsDriveTheRendererSetup() {
    final Canvas canvas = Canvas.of(300, 200)
        .place(0, 0, new Paragraph(List.of(new TextRun("X", ComputedStyle.INITIAL)), Alignment.LEFT)).build();
    final String out = new String(Renderers.forProfile(ZebraProfiles.zd421Zpl203()).render(List.of(canvas)),
        StandardCharsets.UTF_8);

    assertTrue(out.contains("^MTD^MNY"), out); // direct thermal + gap, from the profile
    assertTrue(out.contains("^CI28"), out);
    assertTrue(out.contains("^PW300"), out); // min(canvas width, dotsPerLine)
  }

  @Test
  void zd421Epl203GeometryLanguageAndMedia() {
    final PrinterProfile p = ZebraProfiles.zd421Epl203();

    assertEquals("Zebra ZD421 (EPL, 203dpi)", p.name());
    assertEquals(203, p.dpi());
    assertEquals(832, p.dotsPerLine());
    assertEquals(PrinterLanguage.EPL, p.language());
    assertFalse(p.supportsCut());
    assertEquals(MediaType.DIRECT_THERMAL, p.mediaType());
    assertEquals(MediaTracking.GAP, p.mediaTracking());
    assertEquals(0, p.codePage().id()); // EPL I-command selector (DOS 437)
    assertEquals(Charset.forName("IBM437"), p.codePage().charset()); // single-byte, not UTF-8
  }

  @Test
  void zd421Epl300Geometry() {
    final PrinterProfile p = ZebraProfiles.zd421Epl300();

    assertEquals(300, p.dpi());
    assertEquals(1280, p.dotsPerLine());
    assertEquals(PrinterLanguage.EPL, p.language());
  }

  @Test
  void selectsTheEplBackend() {
    assertInstanceOf(EplRenderer.class, Renderers.forProfile(ZebraProfiles.zd421Epl203()));
  }

  @Test
  void eplProfileDrivesTheRendererSetup() {
    final Canvas canvas = Canvas.of(300, 200)
        .place(0, 0, new Paragraph(List.of(new TextRun("X", ComputedStyle.INITIAL)), Alignment.LEFT)).build();
    final String out = new String(Renderers.forProfile(ZebraProfiles.zd421Epl203()).render(List.of(canvas)),
        Charset.forName("IBM437"));

    assertTrue(out.contains("I8,0,001"), out); // single-byte code page from the profile
    assertTrue(out.contains("q300"), out); // min(canvas width, dotsPerLine)
    assertTrue(out.contains("Q200,24"), out); // gap media -> gap 24
  }
}
