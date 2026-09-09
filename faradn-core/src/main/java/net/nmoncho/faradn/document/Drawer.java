package net.nmoncho.faradn.document;

/**
 * Opens a cash drawer by pulsing the printer's drawer-kick connector
 * ({@code ESC p}). {@code pin} selects which connector pin is pulsed: {@code 2}
 * (the usual drawer) or {@code 5}. A printer with no drawer wired to that pin
 * simply ignores the pulse.
 */
public record Drawer(int pin) implements Block {

  public Drawer {
    if (pin != 2 && pin != 5) {
      throw new IllegalArgumentException("pin must be 2 or 5, got " + pin);
    }
  }

  /** A pulse on the usual drawer connector (pin 2). */
  public Drawer() {
    this(2);
  }
}
