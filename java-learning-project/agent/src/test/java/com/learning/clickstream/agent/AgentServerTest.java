package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** POST a snapshot, list it, open it, ask a question, and reject a bad question. */
class AgentServerTest {
  private static final String REASON =
      "queue depth 150 crossed 100 (batches waiting to be sent to the ingest server)";

  private final ObjectMapper json = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final HttpClient http = HttpClient.newHttpClient();

  @TempDir
  Path dir;

  private AgentServer server;

  @BeforeEach
  void start() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    IncidentService service = new IncidentService(
        new ReportStore(dir.resolve("reports.db")),
        new LocalIncidentWriter(),
        spans -> { },
        clock);
    server = AgentServer.start(service, 0);
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void postOpensAReportThatThePageCanRead() throws Exception {
    IncidentSnapshot snapshot = new IncidentSnapshot(
        Instant.parse("2026-11-04T12:00:00Z"), 150, 20, 5, 0, 1.4,
        Map.of("UNAVAILABLE", 5), List.of(REASON));
    HttpResponse<String> created = send("POST", "/api/incidents", json.writeValueAsString(snapshot));
    assertEquals(201, created.statusCode());
    String id = json.readTree(created.body()).get("id").asText();

    HttpResponse<String> list = send("GET", "/api/reports", null);
    assertEquals(200, list.statusCode());
    JsonNode rows = json.readTree(list.body());
    assertEquals(1, rows.size());
    assertEquals(id, rows.get(0).get("id").asText());
    assertTrue(rows.get(0).get("preview").asText().contains("ingress hop"));

    HttpResponse<String> one = send("GET", "/api/reports/" + id, null);
    assertEquals(200, one.statusCode());
    assertTrue(one.body().contains("150"));
    assertTrue(one.body().contains(REASON));

    HttpResponse<String> answer = send(
        "POST", "/api/reports/" + id + "/messages", "{\"question\":\"What should we look at next?\"}");
    assertEquals(200, answer.statusCode());
    assertTrue(answer.body().contains("150"));
    assertTrue(answer.body().contains(REASON));

    HttpResponse<String> page = send("GET", "/", null);
    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains("textContent"));
    assertTrue(page.headers().firstValue("content-type").orElse("").contains("text/html"));
  }

  @Test
  void emptyAndLongQuestionsAre400AndAMissingIdIs404() throws Exception {
    HttpResponse<String> empty = send("POST", "/api/reports/missing/messages", "{\"question\":\"\"}");
    assertEquals(400, empty.statusCode());
    HttpResponse<String> blank = send("POST", "/api/reports/missing/messages", "{\"question\":\"   \"}");
    assertEquals(400, blank.statusCode());
    HttpResponse<String> huge = send(
        "POST", "/api/reports/missing/messages", "{\"question\":\"" + "x".repeat(4_001) + "\"}");
    assertEquals(400, huge.statusCode());
    assertEquals(404, send("GET", "/api/reports/missing", null).statusCode());
    assertEquals(404, send(
        "POST", "/api/reports/missing/messages", "{\"question\":\"Is the queue still high?\"}").statusCode());
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));
    if (body == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
