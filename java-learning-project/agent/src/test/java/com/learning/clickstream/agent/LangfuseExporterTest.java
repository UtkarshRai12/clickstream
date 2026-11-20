package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A local HTTP server stands in for Langfuse. No network leaves this machine. */
class LangfuseExporterTest {
  private static final String REASON =
      "queue depth 150 crossed 100 (batches waiting to be sent to the ingest server)";
  private static final IncidentSnapshot SNAPSHOT = new IncidentSnapshot(
      Instant.parse("2026-11-04T12:00:00Z"), 150, 20, 5, 0, 1.4,
      Map.of("UNAVAILABLE", 5), List.of(REASON));

  private final ObjectMapper json = new ObjectMapper();
  private final List<Captured> requests = new CopyOnWriteArrayList<>();
  private HttpServer sink;

  @TempDir
  Path dir;

  @AfterEach
  void stop() {
    if (sink != null) {
      sink.stop(0);
    }
  }

  @Test
  void openSendsOneAgentSpanAndOneGeneration() throws Exception {
    String host = startSink(200);
    ReportStore store = new ReportStore(dir.resolve("reports.db"));
    IncidentService service = service(store, LangfuseExporter.create(host, "pk-lf-test", "sk-lf-test"));

    StoredReport report = service.open(SNAPSHOT);

    assertEquals(1, requests.size());
    Captured request = requests.get(0);
    assertEquals(LangfuseExporter.PATH, request.path);
    assertEquals("4", request.version);
    String basic = Base64.getEncoder().encodeToString("pk-lf-test:sk-lf-test".getBytes(StandardCharsets.UTF_8));
    assertEquals("Basic " + basic, request.authorization);

    JsonNode spans = json.readTree(request.body)
        .path("resourceSpans").get(0).path("scopeSpans").get(0).path("spans");
    assertEquals(2, spans.size());
    JsonNode root = spans.get(0);
    JsonNode child = spans.get(1);
    assertEquals("agent", attr(root, "langfuse.observation.type"));
    assertEquals("generation", attr(child, "langfuse.observation.type"));
    assertEquals("write-report", child.path("name").asText());
    assertEquals("local", attr(child, "langfuse.observation.model.name"));
    // The template spends no tokens, so the usage attribute is left off.
    assertEquals(null, attr(child, "langfuse.observation.usage_details"));
    assertEquals("none", attr(root, "langfuse.observation.metadata.guardrail"));

    assertEquals(report.traceId(), root.path("traceId").asText());
    assertEquals(report.traceId(), child.path("traceId").asText());
    assertEquals(report.rootSpanId(), root.path("spanId").asText());
    assertEquals(report.rootSpanId(), child.path("parentSpanId").asText());
    assertTrue(root.path("parentSpanId").isMissingNode());
    for (JsonNode span : spans) {
      assertEquals(report.id(), attr(span, "langfuse.session.id"));
      assertEquals("ingress-incident", attr(span, "langfuse.trace.name"));
    }
    assertTrue(attr(child, "langfuse.observation.output").contains(REASON));
  }

  @Test
  void followUpSendsOneGenerationUnderTheStoredRoot() throws Exception {
    String host = startSink(200);
    ReportStore store = new ReportStore(dir.resolve("reports.db"));
    IncidentService service = service(store, LangfuseExporter.create(host, "pk-lf-test", "sk-lf-test"));
    StoredReport report = service.open(SNAPSHOT);

    service.followUp(report.id(), "What should we look at next?").orElseThrow();

    assertEquals(2, requests.size());
    JsonNode spans = json.readTree(requests.get(1).body)
        .path("resourceSpans").get(0).path("scopeSpans").get(0).path("spans");
    assertEquals(1, spans.size());
    JsonNode follow = spans.get(0);
    assertEquals("follow-up", follow.path("name").asText());
    assertEquals(report.traceId(), follow.path("traceId").asText());
    assertEquals(report.rootSpanId(), follow.path("parentSpanId").asText());
    assertEquals(report.id(), attr(follow, "langfuse.session.id"));
  }

  @Test
  void blankKeysSendNothing() throws Exception {
    String host = startSink(200);
    ReportStore store = new ReportStore(dir.resolve("reports.db"));
    IncidentService service = service(store, LangfuseExporter.create(host, "pk-lf-test", " "));

    StoredReport report = service.open(SNAPSHOT);

    assertEquals(0, requests.size());
    assertTrue(store.find(report.id()).isPresent());
  }

  @Test
  void aServerErrorStillLeavesTheRow() throws Exception {
    String host = startSink(500);
    ReportStore store = new ReportStore(dir.resolve("reports.db"));
    IncidentService service = service(store, LangfuseExporter.create(host, "pk-lf-test", "sk-lf-test"));

    StoredReport report = service.open(SNAPSHOT);

    assertEquals(1, requests.size());
    StoredReport stored = store.find(report.id()).orElseThrow();
    assertTrue(stored.body().contains(REASON));
  }

  private IncidentService service(ReportStore store, TraceExporter exporter) {
    Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    return new IncidentService(store, new LocalIncidentWriter(), exporter, clock);
  }

  /** Returns the host URL, with a trailing slash to prove the exporter trims it. */
  private String startSink(int status) throws Exception {
    sink = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    sink.createContext("/", exchange -> {
      requests.add(new Captured(
          exchange.getRequestURI().getPath(),
          exchange.getRequestHeaders().getFirst("Authorization"),
          exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version"),
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
    sink.start();
    return "http://127.0.0.1:" + sink.getAddress().getPort() + "/";
  }

  private static String attr(JsonNode span, String key) {
    for (JsonNode attribute : span.path("attributes")) {
      if (key.equals(attribute.path("key").asText())) {
        return attribute.path("value").path("stringValue").asText();
      }
    }
    return null;
  }

  private record Captured(String path, String authorization, String version, String body) {}
}
