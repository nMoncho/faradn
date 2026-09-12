//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.zpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.document.Canvas;
import net.nmoncho.faradn.document.ComputedStyle.Alignment;
import net.nmoncho.faradn.printer.MediaTracking;
import net.nmoncho.faradn.printer.MediaType;

class ZplCommandsTest {

  @Test
  void framingConstants() {
    assertEquals("^XA", ZplCommands.START);
    assertEquals("^XZ", ZplCommands.END);
    assertEquals("^MUd", ZplCommands.UNITS_DOTS);
  }

  @Test
  void comment() {
    assertEquals("^FXfrom faradn^FS", ZplCommands.comment("from faradn"));
  }

  @Test
  void printWidthAndLabelLength() {
    assertEquals("^PW832", ZplCommands.printWidth(832));
    assertEquals("^LL400", ZplCommands.labelLength(400));
    assertThrows(IllegalArgumentException.class, () -> ZplCommands.printWidth(0));
    assertThrows(IllegalArgumentException.class, () -> ZplCommands.labelLength(0));
  }

  @Test
  void mediaTracking() {
    assertEquals("^MNN", ZplCommands.mediaTracking(MediaTracking.CONTINUOUS));
    assertEquals("^MNY", ZplCommands.mediaTracking(MediaTracking.GAP));
    assertEquals("^MNM", ZplCommands.mediaTracking(MediaTracking.MARK));
  }

  @Test
  void mediaType() {
    assertEquals("^MTD", ZplCommands.mediaType(MediaType.DIRECT_THERMAL));
    assertEquals("^MTT", ZplCommands.mediaType(MediaType.THERMAL_TRANSFER));
  }

  @Test
  void darknessIsTwoDigits() {
    assertEquals("~SD00", ZplCommands.darkness(0));
    assertEquals("~SD08", ZplCommands.darkness(8));
    assertEquals("~SD30", ZplCommands.darkness(30));
    assertThrows(IllegalArgumentException.class, () -> ZplCommands.darkness(-1));
    assertThrows(IllegalArgumentException.class, () -> ZplCommands.darkness(31));
  }

  @Test
  void encoding() {
    assertEquals("^CI28", ZplCommands.encodingUtf8());
    assertEquals("^CI13", ZplCommands.encoding(13));
  }

  @Test
  void quantityAndLabelHomeAndOrientation() {
    assertEquals("^PQ1", ZplCommands.quantity(1));
    assertEquals("^LH0,0", ZplCommands.labelHome(0, 0));
    assertEquals("^PON", ZplCommands.printOrientationNormal());
    assertEquals("^POI", ZplCommands.printOrientationInverted());
  }

  @Test
  void fieldOrigin() {
    assertEquals("^FO50,60", ZplCommands.fieldOrigin(50, 60));
    assertThrows(IllegalArgumentException.class, () -> ZplCommands.fieldOrigin(-1, 0));
  }

  @Test
  void orientationLetters() {
    assertEquals('N', ZplCommands.orientation(Canvas.Direction.NORMAL));
    assertEquals('R', ZplCommands.orientation(Canvas.Direction.ROTATE_90_CW));
    assertEquals('I', ZplCommands.orientation(Canvas.Direction.ROTATE_180));
    assertEquals('B', ZplCommands.orientation(Canvas.Direction.ROTATE_90_CCW));
  }

  @Test
  void font() {
    assertEquals("^A0N,30,30", ZplCommands.font('0', Canvas.Direction.NORMAL, 30, 30));
    assertEquals("^AAR,24,12", ZplCommands.font('A', Canvas.Direction.ROTATE_90_CW, 24, 12));
    assertEquals("^CF0,30,30", ZplCommands.defaultFont('0', 30, 30));
  }

  @Test
  void justificationLetters() {
    assertEquals('L', ZplCommands.justification(Alignment.LEFT));
    assertEquals('C', ZplCommands.justification(Alignment.CENTER));
    assertEquals('R', ZplCommands.justification(Alignment.RIGHT));
  }

  @Test
  void fieldDataAndBlock() {
    assertEquals("^FDHello^FS", ZplCommands.fieldData("Hello"));
    assertEquals("^FB400,3,0,L,0", ZplCommands.fieldBlock(400, 3, 0, 'L', 0));
  }

  @Test
  void graphicBox() {
    assertEquals("^GB100,50,2,B,0", ZplCommands.graphicBox(100, 50, 2));
    assertEquals("^GB100,50,2,B,3", ZplCommands.graphicBox(100, 50, 2, 'B', 3));
  }
}
