//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.epl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.document.Canvas;

class EplCommandsTest {

  @Test
  void clearBuffer() {
    assertEquals("N", EplCommands.CLEAR_BUFFER);
  }

  @Test
  void labelWidthAndLength() {
    assertEquals("q400", EplCommands.labelWidth(400));
    assertEquals("Q200,24", EplCommands.labelLength(200, 24));
    assertEquals("Q200,0", EplCommands.labelLength(200, 0));
    assertEquals("Q200,B16", EplCommands.labelLengthBlackMark(200, 16));
    assertThrows(IllegalArgumentException.class, () -> EplCommands.labelWidth(0));
  }

  @Test
  void speedDensityOrientationReference() {
    assertEquals("S3", EplCommands.speed(3));
    assertEquals("D8", EplCommands.density(8));
    assertThrows(IllegalArgumentException.class, () -> EplCommands.density(16));
    assertEquals("ZT", EplCommands.printOrientation(false));
    assertEquals("ZB", EplCommands.printOrientation(true));
    assertEquals("R0,0", EplCommands.reference(0, 0));
  }

  @Test
  void optionsAndCodePageAndPrint() {
    assertEquals("OD", EplCommands.options("D"));
    assertEquals("O", EplCommands.options(""));
    assertEquals("O", EplCommands.options(null));
    assertEquals("I8,0,001", EplCommands.codePage(8, "0", "001"));
    assertEquals("I8,A,001", EplCommands.codePage(8, "A", "001"));
    assertThrows(IllegalArgumentException.class, () -> EplCommands.codePage(6, "0", "001"));
    assertEquals("P1", EplCommands.print(1));
  }

  @Test
  void text() {
    assertEquals("A50,30,0,3,1,1,N,\"HI\"", EplCommands.text(50, 30, 0, 3, 1, 1, false, "HI"));
    assertEquals("A0,0,1,3,2,2,R,\"X\"", EplCommands.text(0, 0, 1, 3, 2, 2, true, "X"));
    assertThrows(IllegalArgumentException.class, () -> EplCommands.text(0, 0, 4, 3, 1, 1, false, "X"));
  }

  @Test
  void textEscapesQuotesAndBackslashesAndStripsLineBreaks() {
    // input a"b\c -> a\"b\\c ; a CR/LF in the middle is removed.
    assertEquals("A0,0,0,3,1,1,N,\"a\\\"b\\\\c\"", EplCommands.text(0, 0, 0, 3, 1, 1, false, "a\"b\\c"));
    assertEquals("A0,0,0,3,1,1,N,\"ab\"", EplCommands.text(0, 0, 0, 3, 1, 1, false, "a\r\nb"));
  }

  @Test
  void boxAndLines() {
    assertEquals("X10,10,2,50,60", EplCommands.box(10, 10, 2, 50, 60));
    assertEquals("LO0,0,100,2", EplCommands.lineBlack(0, 0, 100, 2));
    assertEquals("LW0,0,100,2", EplCommands.lineWhite(0, 0, 100, 2));
    assertEquals("LE0,0,100,2", EplCommands.lineXor(0, 0, 100, 2));
  }

  @Test
  void rotationParameter() {
    assertEquals(0, EplCommands.rotation(Canvas.Direction.NORMAL));
    assertEquals(1, EplCommands.rotation(Canvas.Direction.ROTATE_90_CW));
    assertEquals(2, EplCommands.rotation(Canvas.Direction.ROTATE_180));
    assertEquals(3, EplCommands.rotation(Canvas.Direction.ROTATE_90_CCW));
  }
}
