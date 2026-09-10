//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.escpos.commands;

import net.nmoncho.faradn.printer.command.Code;
import net.nmoncho.faradn.printer.command.ParametricCode;
import net.nmoncho.faradn.printer.command.SimpleCode;
import net.nmoncho.faradn.printer.escpos.commands.CharacterCommands.MotionUnit;

public class LineSpacingCommands {

  public static final Code DEFAULT_LINE_SPACING = new SimpleCode(new byte[] { Code.ESC, 0x32 });
  public static final ParametricCode<MotionUnit> SET_LINE_SPACING = new ParametricCode<>(new byte[] { Code.ESC, 0x33 });

}
