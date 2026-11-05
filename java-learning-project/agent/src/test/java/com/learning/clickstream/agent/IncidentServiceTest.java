package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The local writer and a recorder that never opens a socket.
 * The stored body has the queue number and the reason. A follow-up quotes the snapshot.
 */
class IncidentServiceTest {
  private static final String REASON =
      "queue depth 150 crossed 100 (batches waiting to be sent to the ingest server)";
  private static final IncidentSnapshot SNAPSHOT = new IncidentSnapshot(
      Instant.parse("2026-11-04T12:00:00Z"),
      150,
      20,
      5,
      0,
      1.4,
      Map.of("UNAVAILABLE", 5),
      List.of(REASON));

  @TempDir
  Path dir;

  private ReportStore store;
  private RecordingExporter exporter;
  private IncidentService service;

  @BeforeEach
  void open() {
    store = new ReportStore(dir.resolve("reports.db"));
    exporter = new RecordingExporter();
    Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    service = new IncidentService(store, new LocalIncidentWriter(), exporter, clock);
  }

  @Test
  void openStoresTheQueueNumberAndTheReason() {
    StoredReport report = service.open(SNAPSHOT);

    assertTrue(report.body().contains("150"));
    assertTrue(report.body().contains(REASON));
    assertTrue(report.body().contains("already returned 202"));
    assertEquals("local", report.model());
    assertTrue(report.traceId().matches("[0-9a-f]{32}"));
    assertTrue(report.rootSpanId().matches("[0-9a-f]{16}"));

    assertEquals(1, exporter.calls.size());
    List<Observation> spans = exporter.calls.get(0);
    assertEquals(2, spans.size());
    Observation root = spans.get(0);
    Observation child = spans.get(1);
    assertEquals(Observation.AGENT, root.type());
    assertEquals(Observation.TRACE, root.name());
    assertEquals(report.id(), root.sessionId());
    assertEquals(report.traceId(), root.traceId());
    assertEquals(Observation.WRITE_REPORT, child.name());
    assertEquals(Observation.GENERATION, child.type());
    assertEquals(root.spanId(), child.parentSpanId());
    assertEquals(report.id(), child.sessionId());
    assertTrue(child.output().contains(REASON));
  }

  @Test
  void followUpStoresTheQuestionAndQuotesTheSnapshot() {
    StoredReport report = service.open(SNAPSHOT);
    String question = "What should we look at next?";

    ChatMessage answer = service.followUp(report.id(), question).orElseThrow();

    StoredReport stored = store.find(report.id()).orElseThrow();
    assertEquals(3, stored.messages().size());
    assertEquals("user", stored.messages().get(1).role());
    assertEquals(question, stored.messages().get(1).content());
    assertEquals(answer, stored.messages().get(2));
    assertTrue(answer.content().contains("150"));
    assertTrue(answer.content().contains(REASON));
    assertTrue(answer.content().contains("UNAVAILABLE 5"));
    assertTrue(!question.contains("150"));

    assertEquals(2, exporter.calls.size());
    List<Observation> follow = exporter.calls.get(1);
    assertEquals(1, follow.size());
    assertEquals(Observation.FOLLOW_UP, follow.get(0).name());
    assertEquals(report.traceId(), follow.get(0).traceId());
    assertEquals(report.rootSpanId(), follow.get(0).parentSpanId());
    assertEquals(report.id(), follow.get(0).sessionId());
    assertTrue(service.followUp("missing", question).isEmpty());
  }

  /** Records each export call. There is no HTTP client in this class. */
  private static final class RecordingExporter implements TraceExporter {
    private final List<List<Observation>> calls = new ArrayList<>();

    @Override
    public void export(List<Observation> spans) {
      calls.add(List.copyOf(spans));
    }
  }
}
