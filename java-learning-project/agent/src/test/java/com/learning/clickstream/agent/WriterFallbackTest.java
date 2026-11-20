package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A model writer that throws must not lose the report. The local text is stored instead. */
class WriterFallbackTest {
  private static final String REASON = "dropped batches crossed 1 (this batch will not be sent again)";
  private static final IncidentSnapshot SNAPSHOT = new IncidentSnapshot(
      Instant.parse("2026-11-04T12:00:00Z"), 12, 8, 3, 1, 0.6,
      Map.of("UNAVAILABLE", 3), List.of(REASON));

  @TempDir
  Path dir;

  @Test
  void aThrowingWriterStoresTheLocalReport() {
    ReportStore store = new ReportStore(dir.resolve("reports.db"));
    IncidentService service = service(store, new ThrowingWriter());

    StoredReport report = service.open(SNAPSHOT);

    assertEquals("local", report.model());
    assertEquals(new LocalIncidentWriter().writeReport(SNAPSHOT), report.body());
    assertTrue(store.find(report.id()).isPresent());
  }

  @Test
  void aThrowingWriterStillAnswersTheFollowUpLocally() {
    ReportStore store = new ReportStore(dir.resolve("reports.db"));
    IncidentService service = service(store, new ThrowingWriter());
    StoredReport report = service.open(SNAPSHOT);

    ChatMessage answer = service.followUp(report.id(), "Was a batch lost?").orElseThrow();

    assertTrue(answer.content().startsWith("Using the stored snapshot only."));
    assertTrue(answer.content().contains(REASON));
  }

  @Test
  void noKeyMeansTheLocalWriter() {
    assertEquals(null, IncidentWriters.apiKey(Map.of("GEMINI_API_KEY", " ")));
    assertEquals("k2", IncidentWriters.apiKey(Map.of("GEMINI_AI_KEY", "k2")));
    assertEquals("k1", IncidentWriters.apiKey(Map.of("GEMINI_API_KEY", "k1", "GEMINI_AI_KEY", "k2")));
    IncidentWriter writer = IncidentWriters.create(
        null, IncidentWriters.DEFAULT_MODEL, null, IncidentWriters.DEFAULT_TIMEOUT, 0);
    assertEquals("local", writer.modelName());
  }

  private IncidentService service(ReportStore store, IncidentWriter writer) {
    Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    return new IncidentService(store, writer, spans -> { }, clock);
  }

  /** Stands in for Gemini being down or the key being wrong. */
  private static final class ThrowingWriter implements IncidentWriter {
    @Override
    public String modelName() {
      return "gemini-2.5-flash";
    }

    @Override
    public String writeReport(IncidentSnapshot snapshot) {
      throw new IllegalStateException("model unavailable");
    }

    @Override
    public String answer(FollowUpQuery query) {
      throw new IllegalStateException("model unavailable");
    }
  }
}
