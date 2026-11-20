package com.learning.clickstream.agent.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.agent.ChatMessage;
import com.learning.clickstream.agent.FollowUpQuery;
import com.learning.clickstream.agent.IncidentService;
import com.learning.clickstream.agent.IncidentSnapshot;
import com.learning.clickstream.agent.IncidentWriter;
import com.learning.clickstream.agent.LocalIncidentWriter;
import com.learning.clickstream.agent.Observation;
import com.learning.clickstream.agent.ReportStore;
import com.learning.clickstream.agent.StoredReport;
import com.learning.clickstream.agent.WriterResult;
import com.learning.clickstream.agent.guard.TopicGuard.Topic;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fakes stand in for the embedding model and the LLM gate. */
class TopicGuardTest {
  private static final IncidentSnapshot SNAPSHOT = new IncidentSnapshot(
      Instant.parse("2026-11-06T15:50:00Z"), 0, 3, 3, 1, 0.09,
      Map.of("UNAVAILABLE", 3), List.of("dropped batches crossed 1"));

  @TempDir
  Path dir;

  private int databases;

  @Test
  void aSureOffNeverCallsTheWriter() {
    Counting writer = new Counting();
    List<List<Observation>> exported = new ArrayList<>();
    IncidentService service = service(writer, exported, question -> List.of(Topic.OFF), question -> true);

    ChatMessage answer = ask(service, "Tell me a joke");

    assertEquals(0, writer.answers);
    assertEquals(TopicGuard.REFUSAL, answer.content());
    assertEquals("off_topic", exported.get(1).get(0).metadata().get("guardrail"));
    assertEquals("span", exported.get(1).get(0).type());
  }

  @Test
  void aSureOnCallsTheWriter() {
    Counting writer = new Counting();
    IncidentService service = service(writer, new ArrayList<>(), question -> List.of(Topic.ON), question -> false);

    ChatMessage answer = ask(service, "How many attempts failed?");

    assertEquals(1, writer.answers);
    assertTrue(answer.content().contains("Attempts"));
  }

  @Test
  void anUnsureQuestionUsesTheGate() {
    Counting writer = new Counting();
    TopicGuard.Labels unsure = question -> List.of();

    ChatMessage refused = ask(service(writer, new ArrayList<>(), unsure, question -> false), "huh");
    assertEquals(TopicGuard.REFUSAL, refused.content());
    assertEquals(0, writer.answers);

    ChatMessage allowed = ask(service(writer, new ArrayList<>(), unsure, question -> true), "and the queue?");
    assertEquals(1, writer.answers);
    assertTrue(allowed.content().contains("Attempts"));
  }

  @Test
  void onlyOffRefusesTheShortReply() {
    assertTrue(LlmTopicGate.allows(null));
    assertTrue(LlmTopicGate.allows("MAYBE"));
    assertTrue(LlmTopicGate.allows("ON"));
    assertTrue(!LlmTopicGate.allows(" off topic"));
  }

  private ChatMessage ask(IncidentService service, String question) {
    StoredReport report = service.open(SNAPSHOT);
    return service.followUp(report.id(), question).orElseThrow();
  }

  private IncidentService service(
      Counting writer,
      List<List<Observation>> exported,
      TopicGuard.Labels labels,
      TopicGuard.Gate gate) {
    Clock clock = Clock.fixed(Instant.parse("2026-11-06T15:50:00Z"), ZoneOffset.UTC);
    return new IncidentService(
        new ReportStore(dir.resolve("reports-" + databases++ + ".db")),
        writer,
        exported::add,
        clock,
        new TopicGuard(labels, gate));
  }

  private static final class Counting implements IncidentWriter {
    private int answers;

    @Override
    public String modelName() {
      return "gemini-2.5-flash";
    }

    @Override
    public WriterResult writeReport(IncidentSnapshot snapshot) {
      return new LocalIncidentWriter().writeReport(snapshot);
    }

    @Override
    public WriterResult answer(FollowUpQuery query) {
      answers++;
      return WriterResult.model("Attempts 3, failures 3.", "gemini-2.5-flash", 4, 2, null, null);
    }
  }
}
