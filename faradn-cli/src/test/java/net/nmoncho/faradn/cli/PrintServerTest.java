//
// SPDX-FileCopyrightText: Copyright 2026 the original author or authors
// SPDX-License-Identifier: MIT
//

package net.nmoncho.faradn.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.ImagePolicy;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.transport.DumpTransport;
import net.nmoncho.faradn.transport.PrinterStatus;
import net.nmoncho.faradn.transport.Transport;
import net.nmoncho.faradn.transport.TransportException;

public class PrintServerTest {

  private PrintServer server;
  private DumpTransport transport;

  @BeforeEach
  void setUp() throws IOException {
    Image.policy(ImagePolicy.DATA_URIS_ONLY); // the server renders untrusted input
    transport = new DumpTransport();
    server = new PrintServer(0, PrinterProfile.load("TM-T88V").orElseThrow(), () -> transport);
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop();
    Image.policy(ImagePolicy.DATA_URIS_ONLY);
  }

  @Test
  void bindsToLoopbackByDefault() {
    assertTrue(server.address().getAddress().isLoopbackAddress(),
        "the no-auth server must not be exposed on all interfaces by default");
  }

  @Test
  void remoteImageIsRefusedAndTheErrorIsGeneric() throws IOException {
    // An attacker-supplied remote <img src> must not be fetched (SSRF), and the
    // 500 body must not leak the target URL back to the client.
    Response response = post("/print", "<img src=\"http://169.254.169.254/latest/meta-data/\">");

    assertEquals(500, response.status());
    assertTrue(response.body().contains("internal error"), response.body());
    assertFalse(response.body().contains("169.254.169.254"), "the response must not echo the SSRF target");
  }

  @Test
  void healthReturnsOk() throws IOException {
    Response response = get("/health");

    assertEquals(200, response.status());
    assertTrue(response.body().contains("\"status\":\"ok\""), response.body());
  }

  @Test
  void printRendersTheBodyAndSendsToTheTransport() throws IOException {
    Response response = post("/print", "<h1>Hi</h1>");

    assertEquals(200, response.status());
    assertTrue(response.body().contains("\"status\":\"printed\""), response.body());

    byte[] bytes = transport.bytes();
    assertEquals(0x1B, bytes[0] & 0xFF); // ESC
    assertEquals(0x40, bytes[1] & 0xFF); // @ (initialize)
    assertTrue(bytes.length > 5);
  }

  @Test
  void printRejectsNonPost() throws IOException {
    assertEquals(405, get("/print").status());
  }

  @Test
  void rejectsABodyOverTheSizeLimitWith413() throws IOException {
    // A body larger than MAX_BODY_BYTES (5 MiB) must be refused before rendering,
    // so an oversized upload cannot exhaust memory. 6 MiB of '.' is comfortably
    // over the cap.
    String tooBig = ".".repeat(6 * 1024 * 1024);
    Response response = post("/print", tooBig);

    assertEquals(413, response.status());
    assertTrue(response.body().contains("too large"), response.body());
  }

  @Test
  void returns409WhenThePrinterIsNotReady() throws IOException {
    restartWith(new NotReadyTransport());
    Response response = post("/print", "<h1>Hi</h1>");

    assertEquals(409, response.status());
    assertTrue(response.body().contains("not-ready"), response.body());
  }

  @Test
  void returns500WhenTheTransportFails() throws IOException {
    // A failure while writing to the printer must map to a generic 500 with the
    // detail kept server-side, never leaking the exception text to the client.
    restartWith(new FailingTransport());
    Response response = post("/print", "<h1>Hi</h1>");

    assertEquals(500, response.status());
    assertTrue(response.body().contains("internal error"), response.body());
    assertFalse(response.body().contains("simulated"), "the response must not leak the internal error detail");
  }

  @Test
  void skipsTheStatusPollForAStarProfile() throws IOException {
    // Even a transport that reports not-ready is never polled for a Star profile
    // (StarPRNT has no DLE EOT), so the job prints instead of a 409.
    CountingStatusTransport star = new CountingStatusTransport();
    server.stop();
    server = new PrintServer(0, PrinterProfile.load("star-tsp143iv").orElseThrow(), () -> star);
    server.start();

    Response response = post("/print", "<h1>Hi</h1>");

    assertEquals(200, response.status());
    assertTrue(response.body().contains("\"status\":\"printed\""), response.body());
    assertEquals(0, star.statusPolls.get(), "Star jobs must not poll status");
  }

  /** Replaces the default server with one backed by {@code transport}. */
  private void restartWith(Transport transport) throws IOException {
    server.stop();
    server = new PrintServer(0, PrinterProfile.load("TM-T88V").orElseThrow(), () -> transport);
    server.start();
  }

  /** Reports not-ready and counts status polls, but accepts writes. */
  private static final class CountingStatusTransport implements Transport {
    final AtomicInteger statusPolls = new AtomicInteger();

    @Override
    public void write(byte[] payload) {
      // accepted
    }

    @Override
    public PrinterStatus status() {
      statusPolls.incrementAndGet();
      return new PrinterStatus(true, false, true, false, false); // paper end (not ready)
    }

    @Override
    public void close() {
    }
  }

  /** A printer that is reachable but reports out-of-paper, so it is not ready. */
  private static final class NotReadyTransport implements Transport {
    @Override
    public void write(byte[] payload) {
      throw new AssertionError("must not write to a not-ready printer");
    }

    @Override
    public PrinterStatus status() {
      return new PrinterStatus(true, false, true, false, false); // paper end
    }

    @Override
    public void close() {
    }
  }

  /** A ready printer whose write fails, exercising the 500 path. */
  private static final class FailingTransport implements Transport {
    @Override
    public void write(byte[] payload) {
      throw new TransportException("simulated write failure");
    }

    @Override
    public PrinterStatus status() {
      return PrinterStatus.READY;
    }

    @Override
    public void close() {
    }
  }

  private Response get(String path) throws IOException {
    return request("GET", path, null);
  }

  private Response post(String path, String body) throws IOException {
    return request("POST", path, body);
  }

  private Response request(String method, String path, String body) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + server.port() + path).toURL()
        .openConnection();
    connection.setRequestMethod(method);
    if (body != null) {
      connection.setDoOutput(true);
      try (OutputStream out = connection.getOutputStream()) {
        out.write(body.getBytes(StandardCharsets.UTF_8));
      }
    }
    int status = connection.getResponseCode();
    InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
    String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    connection.disconnect();
    return new Response(status, responseBody);
  }

  private record Response(int status, String body) {
  }
}
