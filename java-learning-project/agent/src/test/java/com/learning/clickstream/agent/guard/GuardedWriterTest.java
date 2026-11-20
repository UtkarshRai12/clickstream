package com.learning.clickstream.agent.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.agent.FollowUpQuery;
import com.learning.clickstream.agent.IncidentSnapshot;
import com.learning.clickstream.agent.IncidentWriter;
import com.learning.clickstream.agent.LocalIncidentWriter;
import com.learning.clickstream.agent.WriterResult;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

/** A fake writer. No model, and no network. */
class GuardedWriterTest {
  private static final IncidentSnapshot SNAPSHOT = new IncidentSnapshot(
      Instant.parse("2026-11-06T15:50:00Z"), 0, 3, 3, 1, 0.09,
      Map.of("UNAVAILABLE", 3), List.of("dropped batches crossed 1"));

  @Test
  void aCleanRetryIsKeptAndTheTokensAddUp() {
    Script script = new Script();
    script.reports.add(result("Dropped 7."));
    script.reports.add(result("Dropped 1."));

    WriterResult out = new GuardedWriter(script).writeReport(SNAPSHOT);

    assertEquals(2, script.calls);
    assertEquals("Dropped 1.", out.text());
    assertEquals("gemini-2.5-flash", out.model());
    assertEquals(20, out.inputTokens());
    assertEquals(8, out.outputTokens());
    assertEquals("none", out.guardrail());
  }

  @Test
  void twoBadReportsFallBackToTheLocalText() {
    Script script = new Script();
    script.reports.add(result("Dropped 7."));
    script.reports.add(result("Dropped 9."));

    WriterResult out = new GuardedWriter(script).writeReport(SNAPSHOT);

    assertEquals(2, script.calls);
    assertEquals(new LocalIncidentWriter().writeReport(SNAPSHOT).text(), out.text());
    assertEquals("local", out.model());
    assertEquals("gemini-2.5-flash", out.billedModel());
    assertEquals(20, out.inputTokens());
    assertEquals("numbers", out.guardrail());
    assertTrue(out.text().contains("dropped 1"));
  }

  @Test
  void aCleanFirstAnswerIsNotRetried() {
    Script script = new Script();
    script.reports.add(result("Dropped 1."));

    new GuardedWriter(script).writeReport(SNAPSHOT);

    assertEquals(1, script.calls);
  }

  private static WriterResult result(String text) {
    return WriterResult.model(text, "gemini-2.5-flash", 10, 4, "ingress-report-rules", 1);
  }

  private static final class Script implements IncidentWriter {
    private final Queue<WriterResult> reports = new ArrayDeque<>();
    private int calls;

    @Override
    public String modelName() {
      return "gemini-2.5-flash";
    }

    @Override
    public WriterResult writeReport(IncidentSnapshot snapshot) {
      calls++;
      return reports.remove();
    }

    @Override
    public WriterResult answer(FollowUpQuery query) {
      throw new AssertionError("answer was not part of this test");
    }
  }
}
