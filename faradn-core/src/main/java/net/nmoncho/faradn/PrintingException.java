package net.nmoncho.faradn;

public class PrintingException extends RuntimeException {

  public PrintingException(String message) {
    super(message);
  }

  public PrintingException(String message, Throwable cause) {
    super(message, cause);
  }

  public PrintingException(Throwable cause) {
    super(cause);
  }
}
