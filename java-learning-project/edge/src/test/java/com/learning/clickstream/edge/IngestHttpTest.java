package com.learning.clickstream.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.edge.auth.SecretHashes;
import com.learning.clickstream.edge.config.ApplicationConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * HTTP rules that do not need Doris. 202 is returned while the gRPC target is closed,
 * which is the fire-and-forget boundary: the collector is not waiting on the server.
 */
class IngestHttpTest {
  @TempDir
  Path temp;

  private EdgeServer server;
  private HttpClient http;
  private String token;

  @BeforeEach
  void start() throws Exception {
    Path keys = temp.resolve("api-keys.csv");
    Files.writeString(keys, "demo-ingest,tenant-a,INGEST," + SecretHashes.sha256Hex("ingest-dev-secret") + "\n");
    ApplicationConfig config = ApplicationConfig.builder()
        .httpPort(0)
        .jwtHmacSecret("test-hmac-secret-must-be-32-bytes")
        .ingestTtlSeconds(900)
        .reportTtlSeconds(3600)
        .apiKeyFile(keys)
        .grpcTarget("127.0.0.1:1")
        .internalToken("test-internal-token")
        .ingestBatchCap(2)
        .build();
    server = EdgeServer.start(config, Clock.systemUTC());
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    token = tokenFrom(post("/v1/token", null, "ingest-dev-secret"));
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void oneEventIsAcceptedBeforeAnyServerAck() throws Exception {
    HttpResponse<String> response = post("/v1/events", event("e1"), token);
    assertEquals(202, response.statusCode());
    assertTrue(response.body().contains("\"accepted\":1"));
  }

  @Test
  void aBadSecondItemRejectsTheWholeBatch() throws Exception {
    String body = "{\"events\":[" + event("e1") + "," + event("") + "]}";
    HttpResponse<String> response = post("/v1/events:batch", body, token);
    assertEquals(400, response.statusCode());
    assertTrue(response.body().contains("\"index\":1"));
  }

  @Test
  void anOversizedBatchIs413() throws Exception {
    String body = "{\"events\":[" + event("e1") + "," + event("e2") + "," + event("e3") + "]}";
    HttpResponse<String> response = post("/v1/events:batch", body, token);
    assertEquals(413, response.statusCode());
    assertTrue(response.body().contains("batch_too_large"));
  }

  private HttpResponse<String> post(String path, String body, String bearerOrNull) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
        .timeout(Duration.ofSeconds(2))
        .POST(body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    if ("/v1/token".equals(path)) {
      builder.header("X-Api-Key", "ingest-dev-secret");
    }
    if (bearerOrNull != null && !"/v1/token".equals(path)) {
      builder.header("Authorization", "Bearer " + bearerOrNull);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String event(String eventId) {
    return "{\"event_id\":\"" + eventId
        + "\",\"user_id\":\"ada\",\"event_type\":\"click\",\"occurred_at\":\"2026-10-24T12:00:00Z\"}";
  }

  private static String tokenFrom(HttpResponse<String> response) {
    assertEquals(200, response.statusCode(), response.body());
    String key = "\"token\":\"";
    int start = response.body().indexOf(key) + key.length();
    return response.body().substring(start, response.body().indexOf('"', start));
  }
}
