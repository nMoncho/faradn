package io.nmoncho.faradn.transport;

import io.nmoncho.faradn.PrintingException;

/**
 * A failure in the transport layer: connecting, claiming, writing, or reading
 * status.
 */
public class TransportException extends PrintingException {

  public TransportException(String message) {
    super(message);
  }

  public TransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
