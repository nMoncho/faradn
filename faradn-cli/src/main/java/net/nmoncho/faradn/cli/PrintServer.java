//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.nmoncho.faradn.Document;
import net.nmoncho.faradn.printer.Devices;
import net.nmoncho.faradn.printer.UsbPrinter;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.Renderers;
import net.nmoncho.faradn.transport.PrinterStatus;
import net.nmoncho.faradn.transport.Transport;
import net.nmoncho.faradn.transport.TransportException;

/**
 * A small HTTP server that accepts print requests, built on the JDK's own
 * {@code com.sun.net.httpserver} - no web framework, so it stays native-image
 * friendly. Endpoints:
 * <ul>
 * <li>{@code POST /print} - render the HTML request body and print it;</li>
 * <li>{@code GET /printers} - list connected USB printers;</li>
 * <li>{@code GET /health} - liveness check.</li>
 * </ul>
 */
public final class PrintServer {

  private static final Logger log = LoggerFactory.getLogger(PrintServer.class);

  private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

  /**
   * The default bind address: loopback only, so the no-auth server is not
   * exposed.
   */
  public static final String DEFAULT_BIND = "127.0.0.1";

  static {
    // Bound how long a single request/response may take so a slow client
    // (slowloris) cannot tie up the small worker pool indefinitely. Set before
    // the first HttpServer.create so the JDK server reads them.
    setIfAbsent("sun.net.httpserver.maxReqTime", "20");
    setIfAbsent("sun.net.httpserver.maxRspTime", "20");
  }

  private static void setIfAbsent(String key, String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }

  private final HttpServer http;
  private final PrinterProfile profile;
  private final Supplier<Transport> transports;

  /** Binds to {@link #DEFAULT_BIND} (loopback). */
  public PrintServer(int port, PrinterProfile profile, Supplier<Transport> transports) throws IOException {
    this(DEFAULT_BIND, port, profile, transports);
  }

  public PrintServer(String bindHost, int port, PrinterProfile profile, Supplier<Transport> transports)
      throws IOException {
    this.profile = profile;
    this.transports = transports;
    this.http = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
    this.http.createContext("/print", exchange -> handle(exchange, this::print));
    this.http.createContext("/printers", exchange -> handle(exchange, this::printers));
    this.http.createContext("/health", exchange -> handle(exchange, this::health));
    this.http.setExecutor(Executors.newFixedThreadPool(4));
  }

  /** The actual bound address (host and port). */
  public InetSocketAddress address() {
    return http.getAddress();
  }

  public void start() {
    http.start();
  }

  public void stop() {
    http.stop(0);
  }

  public int port() {
    return http.getAddress().getPort();
  }

  private interface Route {
    Response handle(HttpExchange exchange) throws IOException;
  }

  private void handle(HttpExchange exchange, Route route) throws IOException {
    Response response;
    try {
      response = route.handle(exchange);
    } catch (Exception e) {
      // Log the detail server-side; return a generic body so the response cannot
      // become an SSRF/error oracle that leaks internal targets or paths.
      log.warn("Request to {} failed", exchange.getRequestURI(), e);
      response = new Response(500, json("status", "error", "message", "internal error"));
    }
    respond(exchange, response);
  }

  private Response print(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      return new Response(405, json("status", "error", "message", "use POST"));
    }
    final byte[] body = readBody(exchange);
    if (body == null) {
      return new Response(413, json("status", "error", "message", "request body too large"));
    }

    final Document document = Document.from(new String(body, StandardCharsets.UTF_8));
    final byte[] payload = Renderers.forProfile(profile).render(document.blocks(profile.dpi()));

    try (Transport transport = transports.get()) {
      final PrinterStatus status = statusOrNull(transport);
      if (status != null && !status.ready()) {
        return new Response(409, json("status", "not-ready", "message", status.toString()));
      }
      transport.write(payload);
    }
    return new Response(200, json("status", "printed", "bytes", payload.length));
  }

  private Response printers(HttpExchange exchange) {
    final List<UsbPrinter> devices = Devices.list();
    final StringBuilder array = new StringBuilder("[");
    for (int i = 0; i < devices.size(); i++) {
      final UsbPrinter printer = devices.get(i);
      if (i > 0) {
        array.append(",");
      }
      array.append(json("vendor", String.format("0x%04x", printer.vendorId()),
          "product", String.format("0x%04x", printer.productId())));
    }
    return new Response(200, array.append("]").toString());
  }

  private Response health(HttpExchange exchange) {
    return new Response(200, json("status", "ok"));
  }

  private static PrinterStatus statusOrNull(Transport transport) {
    try {
      return transport.status();
    } catch (TransportException e) {
      return null;
    }
  }

  private static byte[] readBody(HttpExchange exchange) throws IOException {
    final byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
    return bytes.length > MAX_BODY_BYTES ? null : bytes;
  }

  private static void respond(HttpExchange exchange, Response response) throws IOException {
    final byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(response.status(), body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private record Response(int status, String body) {
  }

  private static String json(Object... keyValues) {
    final StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append('"').append(keyValues[i]).append("\":");
      final Object value = keyValues[i + 1];
      if (value instanceof Number) {
        sb.append(value);
      } else {
        sb.append('"').append(escape(String.valueOf(value))).append('"');
      }
    }
    return sb.append("}").toString();
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
  }
}
