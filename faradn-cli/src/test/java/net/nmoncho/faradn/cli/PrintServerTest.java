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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.nmoncho.faradn.Image;
import net.nmoncho.faradn.ImagePolicy;
import net.nmoncho.faradn.printer.PrinterProfile;
import net.nmoncho.faradn.transport.DumpTransport;

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
