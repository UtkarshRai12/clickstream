package com.learning.clickstream.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Phase 4 exit check. A good key returns a token, a bad key is 401, and an
 * ingest token is 403 on the report path.
 */
class AuthHttpTest {
  @TempDir
  Path temp;

  private EdgeServer server;
  private HttpClient http;

  @BeforeEach
  void start() throws Exception {
    Path keys = temp.resolve("api-keys.csv");
    Files.writeString(keys, """
        demo-ingest,tenant-a,INGEST,%s
        demo-report,tenant-a,REPORT,%s
        """.formatted(
        SecretHashes.sha256Hex("ingest-dev-secret"),
        SecretHashes.sha256Hex("report-dev-secret")));
    ApplicationConfig config = ApplicationConfig.builder()
        .httpPort(0)
        .jwtHmacSecret("test-hmac-secret-must-be-32-bytes")
        .ingestTtlSeconds(900)
        .reportTtlSeconds(3600)
        .apiKeyFile(keys)
        .build();
    server = EdgeServer.start(config, Clock.systemUTC());
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void goodKeyReturnsAnIngestToken() throws Exception {
    HttpResponse<String> response = postToken("ingest-dev-secret");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"scope\":\"INGEST\""));
    assertTrue(response.body().contains("\"expires_in_seconds\":900"));
  }

  @Test
  void unknownKeyIs401() throws Exception {
    HttpResponse<String> response = postToken("not-a-real-secret");
    assertEquals(401, response.statusCode());
    assertTrue(response.body().contains("unknown_api_key"));
    String scraped = metrics();
    assertTrue(scraped.contains("clickstream_token_rejections_total"));
    assertTrue(scraped.contains("code=\"401\""));
  }

  @Test
  void missingKeyIs401() throws Exception {
    HttpResponse<String> response = postToken(null);
    assertEquals(401, response.statusCode());
    assertTrue(response.body().contains("missing_api_key"));
  }

  @Test
  void ingestTokenCannotReadReports() throws Exception {
    String token = tokenFrom(postToken("ingest-dev-secret"));
    HttpResponse<String> response = get("/v1/reports/daily", token);
    assertEquals(403, response.statusCode());
    assertTrue(response.body().contains("wrong_scope"));
    assertFalse(response.body().contains("tenant-a"));
  }

  @Test
  void reportTokenReachesTheReportServlet() throws Exception {
    String token = tokenFrom(postToken("report-dev-secret"));
    HttpResponse<String> response = get("/v1/reports/daily", token);
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"tenant_id\":\"tenant-a\""));
  }

  @Test
  void missingBearerOnAReportIs401() throws Exception {
    HttpResponse<String> response = get("/v1/reports/daily", null);
    assertEquals(401, response.statusCode());
    assertTrue(response.body().contains("missing_token"));
  }

  @Test
  void emptyIngestBodyIs400() throws Exception {
    String token = tokenFrom(postToken("ingest-dev-secret"));
    HttpResponse<String> one = post("/v1/events", token);
    HttpResponse<String> batch = post("/v1/events:batch", token);
    assertEquals(400, one.statusCode());
    assertEquals(400, batch.statusCode());
    assertTrue(one.body().contains("invalid_json"));
    String scraped = metrics();
    assertTrue(scraped.contains("clickstream_events_rejected_total"));
    assertTrue(scraped.contains("code=\"400\""));
  }

  @Test
  void reportTokenCannotPostEvents() throws Exception {
    String token = tokenFrom(postToken("report-dev-secret"));
    HttpResponse<String> response = post("/v1/events", token);
    assertEquals(403, response.statusCode());
    assertTrue(response.body().contains("wrong_scope"));
  }

  private HttpResponse<String> postToken(String apiKey) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(url("/v1/token"))
        .timeout(Duration.ofSeconds(2))
        .POST(HttpRequest.BodyPublishers.noBody());
    if (apiKey != null) {
      builder.header("X-Api-Key", apiKey);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path, String token) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(url(path)).timeout(Duration.ofSeconds(2)).GET();
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String token) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(url(path))
        .timeout(Duration.ofSeconds(2))
        .POST(HttpRequest.BodyPublishers.noBody());
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String metrics() throws Exception {
    HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + server.metricsPort() + "/metrics"))
        .timeout(Duration.ofSeconds(2))
        .GET()
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return response.body();
  }

  private URI url(String path) {
    return URI.create("http://127.0.0.1:" + server.port() + path);
  }

  private static String tokenFrom(HttpResponse<String> response) {
    assertEquals(200, response.statusCode(), response.body());
    String key = "\"token\":\"";
    int start = response.body().indexOf(key);
    if (start < 0) {
      throw new AssertionError(response.body());
    }
    start += key.length();
    return response.body().substring(start, response.body().indexOf('"', start));
  }
}
