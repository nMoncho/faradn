package io.nmoncho.faradn.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import io.nmoncho.faradn.printer.escpos.Code;
import io.nmoncho.faradn.printer.escpos.commands.StatusCommands;

/**
 * Sends bytes to a network printer over raw TCP (JetDirect, port 9100). Status
 * is read by writing {@code DLE EOT} queries and reading the one-byte replies,
 * under a socket read timeout so a silent printer cannot hang the caller.
 */
public final class NetworkTransport implements Transport {

  public static final int DEFAULT_PORT = 9100;

  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3_000;
  private static final int DEFAULT_STATUS_TIMEOUT_MILLIS = 3_000;

  private final Socket socket;
  private final OutputStream out;
  private final InputStream in;
  private final int statusTimeoutMillis;

  public NetworkTransport(String host) {
    this(host, DEFAULT_PORT);
  }

  public NetworkTransport(String host, int port) {
    this(host, port, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_STATUS_TIMEOUT_MILLIS);
  }

  public NetworkTransport(String host, int port, int connectTimeoutMillis, int statusTimeoutMillis) {
    this.statusTimeoutMillis = statusTimeoutMillis;
    try {
      this.socket = new Socket();
      socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
      this.out = socket.getOutputStream();
      this.in = socket.getInputStream();
    } catch (IOException e) {
      throw new TransportException("Could not connect to printer at " + host + ":" + port, e);
    }
  }

  @Override
  public void write(byte[] payload) {
    try {
      out.write(payload);
      out.flush();
    } catch (IOException e) {
      throw new TransportException("Failed to write to network printer", e);
    }
  }

  @Override
  public PrinterStatus status() {
    try {
      socket.setSoTimeout(statusTimeoutMillis);
      byte printer = query(StatusCommands.PRINTER_STATUS);
      byte offline = query(StatusCommands.OFFLINE_STATUS);
      byte error = query(StatusCommands.ERROR_STATUS);
      byte paper = query(StatusCommands.PAPER_ROLL_STATUS);
      return PrinterStatus.of(printer, offline, error, paper);
    } catch (IOException e) {
      throw new TransportException("Failed to read status from network printer", e);
    }
  }

  private byte query(Code command) throws IOException {
    out.write(command.getCode());
    out.flush();
    int value = in.read();
    if (value < 0) {
      throw new IOException("printer closed the connection during status read");
    }
    return (byte) value;
  }

  @Override
  public void close() {
    try {
      socket.close();
    } catch (IOException ignored) {
      // best effort
    }
  }
}
