package com.learning.clickstream.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.edge.auth.SecretHashes;
import com.learning.clickstream.edge.config.ApplicationConfig;
import com.learning.clickstream.server.IngestServer;
import com.learning.clickstream.server.ServerConfig;
import com.learning.clickstream.server.doris.DorisConfig;
import com.learning.clickstream.server.doris.DorisDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 5 exit check. One event and a batch return 202, then show up in Doris.
 * An oversized batch returns 413 and those ids stay absent.
 * The tenant stored in Doris is the one from the JWT, not a field in the JSON.
 */
class IngestToDorisTest {
  @TempDir
  Path temp;

  @Test
  @Timeout(120)
  void acceptedEventsBecomeVisibleAndRejectedBatchesDoNot() throws Exception {
    String internal = "phase5-internal-token";
    IngestServer ingest = IngestServer.start(ServerConfig.builder()
        .doris(DorisConfig.local())
        .grpcPort(0)
        .internalToken(internal)
        .flushSize(1)
        .flushIntervalMs(200)
        .build());
    EdgeServer edge = null;
    try {
      Path keys = temp.resolve("api-keys.csv");
      Files.writeString(keys, "demo-ingest,tenant-a,INGEST," + SecretHashes.sha256Hex("ingest-dev-secret") + "\n");
      edge = EdgeServer.start(ApplicationConfig.builder()
          .httpPort(0)
          .jwtHmacSecret("test-hmac-secret-must-be-32-bytes")
          .ingestTtlSeconds(900)
          .reportTtlSeconds(3600)
          .apiKeyFile(keys)
          .grpcTarget("127.0.0.1:" + ingest.port())
          .internalToken(internal)
          .ingestBatchCap(2)
          .build(), Clock.systemUTC());
      HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      String token = token(http, edge.port());
      String singleId = "p5-" + UUID.randomUUID();
      String batchA = "p5-" + UUID.randomUUID();
      String batchB = "p5-" + UUID.randomUUID();
      String rejected = "p5-" + UUID.randomUUID();

      HttpResponse<String> one = post(http, edge.port(), "/v1/events", event(singleId, "evil"), token);
      assertEquals(202, one.statusCode(), one.body());
      HttpResponse<String> batch = post(http, edge.port(), "/v1/events:batch",
          "{\"events\":[" + event(batchA, "evil") + "," + event(batchB, "evil") + "]}", token);
      assertEquals(202, batch.statusCode(), batch.body());
      HttpResponse<String> tooBig = post(http, edge.port(), "/v1/events:batch",
          "{\"events\":[" + event(rejected, "ada") + "," + event("x", "ada") + "," + event("y", "ada") + "]}", token);
      assertEquals(413, tooBig.statusCode(), tooBig.body());

      DorisDataSource dataSource = new DorisDataSource(DorisConfig.local());
      assertEquals("tenant-a", awaitTenant(dataSource, singleId));
      assertEquals("tenant-a", awaitTenant(dataSource, batchA));
      assertEquals("tenant-a", awaitTenant(dataSource, batchB));
      Thread.sleep(1500);
      assertNull(tenantNow(dataSource, rejected));
    } finally {
      if (edge != null) {
        edge.close();
      }
      ingest.close();
    }
  }

  private static String token(HttpClient http, int port) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/token"))
        .timeout(Duration.ofSeconds(2))
        .header("X-Api-Key", "ingest-dev-secret")
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    String key = "\"token\":\"";
    int start = response.body().indexOf(key) + key.length();
    return response.body().substring(start, response.body().indexOf('"', start));
  }

  private static HttpResponse<String> post(HttpClient http, int port, String path, String body, String token)
      throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(2))
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String event(String eventId, String userId) {
    return "{\"event_id\":\"" + eventId + "\",\"user_id\":\"" + userId
        + "\",\"event_type\":\"page_view\",\"occurred_at\":\"2026-10-24T12:00:00Z\","
        + "\"tenant_id\":\"evil\",\"properties\":{\"path\":\"/home\"}}";
  }

  private static String awaitTenant(DorisDataSource dataSource, String eventId) throws Exception {
    Instant deadline = Instant.now().plusSeconds(20);
    while (Instant.now().isBefore(deadline)) {
      String tenant = tenantNow(dataSource, eventId);
      if (tenant != null) {
        return tenant;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("event " + eventId + " was not visible in Doris");
  }

  private static String tenantNow(DorisDataSource dataSource, String eventId) throws Exception {
    try (Connection connection = dataSource.open();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT tenant_id FROM clickstream.events WHERE event_id = ?")) {
      statement.setString(1, eventId);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? rows.getString(1) : null;
      }
    }
  }
}
