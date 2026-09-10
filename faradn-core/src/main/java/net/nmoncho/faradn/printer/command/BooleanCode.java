//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.printer.command;

/**
 * {@link net.nmoncho.faradn.printer.command.ParametricCode} implementation that
 * can be turned ON/OFF.
 */
public class BooleanCode extends ParametricCode<Byteable.Boolean> {

  public BooleanCode(byte[] code) {
    super(code);
  }

  /**
   * Turns <em>on</em> this parametric code
   */
  public byte[] turnOn() {
    return getCode(Byteable.Boolean.ON);
  }

  /**
   * Turns <em>off</em> this parametric code
   */
  public byte[] turnOff() {
    return getCode(Byteable.Boolean.OFF);
  }

}
