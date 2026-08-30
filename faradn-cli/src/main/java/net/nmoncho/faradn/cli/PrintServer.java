package net.nmoncho.faradn.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.nmoncho.faradn.Document;
import net.nmoncho.faradn.printer.Devices;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.printer.escpos.EscPosRenderer;
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

  private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

  private final HttpServer http;
  private final PrinterProfile profile;
  private final Supplier<Transport> transports;

  public PrintServer(int port, PrinterProfile profile, Supplier<Transport> transports) throws IOException {
    this.profile = profile;
    this.transports = transports;
    this.http = HttpServer.create(new InetSocketAddress(port), 0);
    this.http.createContext("/print", exchange -> handle(exchange, this::print));
    this.http.createContext("/printers", exchange -> handle(exchange, this::printers));
    this.http.createContext("/health", exchange -> handle(exchange, this::health));
    this.http.setExecutor(Executors.newFixedThreadPool(4));
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
      response = new Response(500, json("status", "error", "message", String.valueOf(e.getMessage())));
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
    final byte[] payload = new EscPosRenderer(profile).render(document.blocks());

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
    final List<UsbDevice> devices = Devices.listPrinterDevices();
    final StringBuilder array = new StringBuilder("[");
    for (int i = 0; i < devices.size(); i++) {
      final UsbDeviceDescriptor descriptor = devices.get(i).getUsbDeviceDescriptor();
      if (i > 0) {
        array.append(",");
      }
      array.append(json("vendor", String.format("0x%04x", descriptor.idVendor() & 0xFFFF),
          "product", String.format("0x%04x", descriptor.idProduct() & 0xFFFF)));
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
