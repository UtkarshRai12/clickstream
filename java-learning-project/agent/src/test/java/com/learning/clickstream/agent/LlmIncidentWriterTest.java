package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real LangChain4j Gemini client against a local stub of the Gemini REST API.
 * The key is fake and no request leaves the machine. This also proves the client
 * runs on the Jackson version this project pins.
 */
class LlmIncidentWriterTest {
  private static final String REASON =
      "gRPC error ratio 0.25 crossed 0.25 (5 failed of the last 20 attempts)";
  private static final IncidentSnapshot SNAPSHOT = new IncidentSnapshot(
      Instant.parse("2026-11-04T12:00:00Z"), 40, 20, 5, 0, 0.3,
      Map.of("UNAVAILABLE", 5), List.of(REASON));

  private final List<Captured> requests = new CopyOnWriteArrayList<>();
  private HttpServer stub;

  @TempDir
  Path dir;

  @AfterEach
  void stop() {
    stub.stop(0);
  }

  @Test
  void reportAndFollowUpBothCarryTheSnapshot() throws Exception {
    String baseUrl = startStub(200, "Five of the last 20 Accept calls failed with UNAVAILABLE.");
    IncidentService service = service(baseUrl);

    StoredReport report = service.open(SNAPSHOT);

    assertEquals(IncidentWriters.DEFAULT_MODEL, report.model());
    assertEquals("Five of the last 20 Accept calls failed with UNAVAILABLE.", report.body());
    Captured first = requests.get(0);
    assertTrue(first.path.endsWith("/models/" + IncidentWriters.DEFAULT_MODEL + ":generateContent"), first.path);
    assertEquals("test-key", first.apiKey);
    assertTrue(first.body.contains("queueDepth"), first.body);
    assertTrue(first.body.contains(REASON), first.body);

    service.followUp(report.id(), "Which status code?").orElseThrow();
    service.followUp(report.id(), "Is the queue high?").orElseThrow();

    assertEquals(3, requests.size());
    String last = requests.get(2).body;
    // The follow-up re-sends the stored snapshot, the report, the earlier turn, and the new question.
    assertTrue(last.contains("queueDepth"), last);
    assertTrue(last.contains(REASON), last);
    assertTrue(last.contains("The report you wrote"), last);
    assertTrue(last.contains("Which status code?"), last);
    assertTrue(last.contains("Is the queue high?"), last);
  }

  @Test
  void tokenCountsComeBackWithTheText() throws Exception {
    String baseUrl = startStub(200, "Five of the last 20 Accept calls failed with UNAVAILABLE.");
    IncidentWriter writer = IncidentWriters.create(
        "test-key", IncidentWriters.DEFAULT_MODEL, baseUrl, Duration.ofSeconds(5), 0);

    WriterResult result = writer.writeReport(SNAPSHOT);

    assertEquals(10, result.inputTokens());
    assertEquals(5, result.outputTokens());
  }

  @Test
  void aGeminiErrorFallsBackToTheLocalReport() throws Exception {
    String baseUrl = startStub(500, "unused");
    IncidentService service = service(baseUrl);

    StoredReport report = service.open(SNAPSHOT);

    assertEquals("local", report.model());
    assertEquals(new LocalIncidentWriter().writeReport(SNAPSHOT).text(), report.body());
  }

  @Test
  void anEmptyAnswerFallsBackToTheLocalReport() throws Exception {
    String baseUrl = startStub(200, "");
    IncidentService service = service(baseUrl);

    StoredReport report = service.open(SNAPSHOT);

    assertEquals("local", report.model());
  }

  private IncidentService service(String baseUrl) {
    IncidentWriter writer = IncidentWriters.create(
        "test-key", IncidentWriters.DEFAULT_MODEL, baseUrl, Duration.ofSeconds(5), 0);
    Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    return new IncidentService(new ReportStore(dir.resolve("reports.db")), writer, spans -> { }, clock);
  }

  /** Answers every generateContent call with one candidate holding {@code text}. */
  private String startStub(int status, String text) throws Exception {
    String body = """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP","index":0}],
         "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15},
         "modelVersion":"gemini-3.6-flash"}
        """.formatted(text);
    byte[] bytes = (status == 200 ? body : "{\"error\":{\"code\":500,\"message\":\"boom\"}}")
        .getBytes(StandardCharsets.UTF_8);
    stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stub.createContext("/", exchange -> {
      requests.add(new Captured(
          exchange.getRequestURI().getPath(),
          exchange.getRequestHeaders().getFirst("x-goog-api-key"),
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    stub.start();
    return "http://127.0.0.1:" + stub.getAddress().getPort() + "/v1beta";
  }

  private record Captured(String path, String apiKey, String body) {}
}
