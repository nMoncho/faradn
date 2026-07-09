package io.nmoncho.faradn.printer.escpos;

/**
 * {@link io.nmoncho.faradn.printer.escpos.ParametricCode} implementation that
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
