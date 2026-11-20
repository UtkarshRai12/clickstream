package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A local server stands in for the Langfuse prompt API. */
class PromptSourceTest {
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void aPromptIsReadWithItsVersion() throws Exception {
    start((method, path, body) -> {
      assertTrue(path.contains("label=production"), path);
      return new Reply(200, "{\"name\":\"ingress-report-rules\",\"version\":4,\"type\":\"text\",\"prompt\":\"from langfuse\"}");
    });

    PromptSource.PromptText text = source().get();

    assertEquals("from langfuse", text.text());
    assertEquals(PromptSource.NAME, text.name());
    assertEquals(4, text.version());
  }

  @Test
  void aMissingPromptIsSeededFromTheHardcodedRules() throws Exception {
    AtomicReference<String> posted = new AtomicReference<>();
    start((method, path, body) -> {
      if ("POST".equals(method)) {
        posted.set(body);
        // Create is the collection. The name is in the body, not the path.
        assertEquals("/api/public/v2/prompts", path);
        return new Reply(200, "{\"name\":\"ingress-report-rules\",\"version\":1,\"type\":\"text\",\"prompt\":\"seeded\"}");
      }
      return new Reply(404, "{\"message\":\"not found\"}");
    });

    PromptSource.PromptText text = source().get();

    assertEquals("seeded", text.text());
    assertEquals(1, text.version());
    assertTrue(posted.get().contains("\"type\":\"text\""), posted.get());
    assertTrue(posted.get().contains("\"labels\":[\"production\"]"), posted.get());
    assertTrue(posted.get().contains("Do not invent a cause"), posted.get());
  }

  @Test
  void aClosedPortReturnsTheHardcodedText() {
    PromptSource source = PromptSource.fromEnv(
        Map.of(
            "LANGFUSE_HOST", "http://127.0.0.1:9",
            "LANGFUSE_PUBLIC_KEY", "pk",
            "LANGFUSE_SECRET_KEY", "sk"),
        LlmIncidentWriter.RULES);

    PromptSource.PromptText text = source.get();

    assertEquals(LlmIncidentWriter.RULES, text.text());
    assertNull(text.name());
    assertNull(text.version());
  }

  private PromptSource source() {
    return PromptSource.fromEnv(
        Map.of(
            "LANGFUSE_HOST", "http://127.0.0.1:" + server.getAddress().getPort(),
            "LANGFUSE_PUBLIC_KEY", "pk",
            "LANGFUSE_SECRET_KEY", "sk"),
        LlmIncidentWriter.RULES);
  }

  private void start(Handler handler) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getRawQuery() == null
          ? exchange.getRequestURI().getPath()
          : exchange.getRequestURI().getPath() + "?" + exchange.getRequestURI().getRawQuery();
      String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      Reply reply = handler.handle(exchange.getRequestMethod(), path, requestBody);
      byte[] bytes = reply.body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(reply.status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
  }

  @FunctionalInterface
  private interface Handler {
    Reply handle(String method, String path, String body);
  }

  private record Reply(int status, String body) {}
}
