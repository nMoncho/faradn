package io.nmoncho.faradn.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class NetworkTransportTest {

  @Test
  void writeSendsThePayloadOverTheSocket() throws Exception {
    byte[] payload = { 0x1B, 0x40, 'H', 'i', 0x0A };

    try (ServerSocket server = new ServerSocket(0)) {
      CompletableFuture<byte[]> received = acceptAndRead(server, payload.length);

      try (NetworkTransport transport = new NetworkTransport("127.0.0.1", server.getLocalPort())) {
        transport.write(payload);
      }

      assertArrayEquals(payload, received.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void statusReadsAndDecodesDleEotReplies() throws Exception {
    // printer ok, offline ok, error ok, paper roll = paper out
    byte[] replies = { 0x12, 0x12, 0x12, 0x72 };

    try (ServerSocket server = new ServerSocket(0)) {
      answerStatusQueries(server, replies);

      try (NetworkTransport transport = new NetworkTransport("127.0.0.1", server.getLocalPort())) {
        PrinterStatus status = transport.status();

        assertTrue(status.paperEnd());
        assertFalse(status.ready());
      }
    }
  }

  private static CompletableFuture<byte[]> acceptAndRead(ServerSocket server, int count) {
    CompletableFuture<byte[]> future = new CompletableFuture<>();
    Thread thread = new Thread(() -> {
      try (Socket socket = server.accept()) {
        future.complete(socket.getInputStream().readNBytes(count));
      } catch (IOException e) {
        future.completeExceptionally(e);
      }
    });
    thread.setDaemon(true);
    thread.start();
    return future;
  }

  private static void answerStatusQueries(ServerSocket server, byte[] replies) {
    Thread thread = new Thread(() -> {
      try (Socket socket = server.accept()) {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        for (byte reply : replies) {
          in.readNBytes(3); // consume DLE EOT n
          out.write(reply);
          out.flush();
        }
      } catch (IOException ignored) {
        // the test closed the socket
      }
    });
    thread.setDaemon(true);
    thread.start();
  }
}
