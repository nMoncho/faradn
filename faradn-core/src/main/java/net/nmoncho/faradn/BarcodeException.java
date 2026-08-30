package net.nmoncho.faradn;

/**
 * Thrown when a barcode cannot be produced: an unknown symbology, or data that
 * is invalid for the requested symbology (wrong characters or length).
 */
public class BarcodeException extends PrintingException {

  public BarcodeException(String message) {
    super(message);
  }
}
