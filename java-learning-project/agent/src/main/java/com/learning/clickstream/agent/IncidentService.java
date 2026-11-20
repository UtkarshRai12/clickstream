package com.learning.clickstream.agent;

import com.learning.clickstream.agent.guard.TopicGuard;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens one report for a crossing and answers a follow-up from the stored
 * snapshot. The trace is exported only after the row is committed.
 */
public final class IncidentService {
  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

  private final ReportStore store;
  private final IncidentWriter writer;
  private final LocalIncidentWriter local = new LocalIncidentWriter();
  private final TraceExporter exporter;
  private final Clock clock;
  private final TopicGuard topics;
  private final SecureRandom random = new SecureRandom();

  /** Tests that are not about topics. Every follow-up is allowed. */
  public IncidentService(ReportStore store, IncidentWriter writer, TraceExporter exporter, Clock clock) {
    this(store, writer, exporter, clock, TopicGuard.allowAll());
  }

  public IncidentService(
      ReportStore store, IncidentWriter writer, TraceExporter exporter, Clock clock, TopicGuard topics) {
    this.store = store;
    this.writer = writer;
    this.exporter = exporter;
    this.clock = clock;
    this.topics = topics;
  }

  /** Newest first. The page uses this for the list and does not poll. */
  public List<ReportSummary> list() {
    return store.list();
  }

  /** Empty when the id is unknown. The servlet turns that into 404. */
  public Optional<StoredReport> find(String id) {
    return store.find(id);
  }

  /** Writes the report and the first assistant message, then exports the root and the child. */
  public StoredReport open(IncidentSnapshot snapshot) {
    // The root span covers the write and the insert. The child covers only the writer call.
    Instant started = clock.instant();
    Written written = write(snapshot);
    Instant writtenAt = clock.instant();
    String id = UUID.randomUUID().toString();
    String traceId = hex(32);
    String rootSpanId = hex(16);
    String snapshotJson = IncidentText.toJson(snapshot);
    StoredReport report = store.insert(
        id, writtenAt, snapshot.reasons(), snapshotJson, written.body, written.model, traceId, rootSpanId);
    Instant stored = clock.instant();
    String prompt = IncidentText.prompt(snapshotJson, null);
    // The row says "local" when the guardrail swapped the text. The span still
    // names the model that was billed, so the tokens have a price.
    export(id, List.of(
        Observation.agent(
            traceId, rootSpanId, id, written.model, snapshotJson, written.body, started, stored,
            written.guardrail),
        Observation.generation(
            traceId, hex(16), rootSpanId, id, Observation.WRITE_REPORT, spanModel(written), prompt,
            written.body, started, writtenAt, written.inputTokens, written.outputTokens,
            written.promptName, written.promptVersion, null)));
    return report;
  }

  /**
   * Stores the question and an answer that quotes the stored snapshot.
   * Empty when the id is unknown. The root span is not exported again.
   */
  public Optional<ChatMessage> followUp(String id, String question) {
    Optional<StoredReport> found = store.find(id);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    StoredReport report = found.get();
    // Off topic never reaches the writer. The refusal is still stored.
    if (!topics.allows(question)) {
      return refuse(id, report, question);
    }
    IncidentSnapshot snapshot = IncidentText.fromJson(report.snapshotJson());
    FollowUpQuery query = new FollowUpQuery(snapshot, report.body(), IncidentText.last(report.messages()), question);
    Instant started = clock.instant();
    Written written = answer(query);
    Instant now = clock.instant();
    store.append(id, "user", question, now);
    ChatMessage assistant = store.append(id, "assistant", written.body, now).orElseThrow();
    export(id, List.of(Observation.generation(
        report.traceId(),
        hex(16),
        report.rootSpanId(),
        id,
        Observation.FOLLOW_UP,
        spanModel(written),
        IncidentText.prompt(report.snapshotJson(), question),
        written.body,
        started,
        now,
        written.inputTokens,
        written.outputTokens,
        written.promptName,
        written.promptVersion,
        written.guardrail)));
    return Optional.of(assistant);
  }

  private Optional<ChatMessage> refuse(String id, StoredReport report, String question) {
    Instant now = clock.instant();
    store.append(id, "user", question, now);
    ChatMessage assistant = store.append(id, "assistant", TopicGuard.REFUSAL, now).orElseThrow();
    export(id, List.of(Observation.refused(
        report.traceId(), hex(16), report.rootSpanId(), id, question, TopicGuard.REFUSAL, now, now)));
    return Optional.of(assistant);
  }

  private Written write(IncidentSnapshot snapshot) {
    try {
      return from(writer.writeReport(snapshot));
    } catch (RuntimeException ex) {
      // A local failure is the last resort, so it propagates and nothing is stored.
      if (writer instanceof LocalIncidentWriter) {
        throw ex;
      }
      log.warn("incident writer failed, storing the local report", ex);
      return from(local.writeReport(snapshot));
    }
  }

  private Written answer(FollowUpQuery query) {
    try {
      return from(writer.answer(query));
    } catch (RuntimeException ex) {
      if (writer instanceof LocalIncidentWriter) {
        throw ex;
      }
      log.warn("follow-up writer failed, storing the local answer", ex);
      return from(local.answer(query));
    }
  }

  private static Written from(WriterResult result) {
    return new Written(
        IncidentText.cap(result.text()),
        result.model(),
        result.billedModel(),
        result.inputTokens(),
        result.outputTokens(),
        result.promptName(),
        result.promptVersion(),
        result.guardrail());
  }

  /** The model that spent the tokens. The row model can be "local" after a swap. */
  private static String spanModel(Written written) {
    return written.billedModel != null ? written.billedModel : written.model;
  }

  /** The export runs after the commit. A sink that throws leaves the row in place. */
  private void export(String id, List<Observation> spans) {
    try {
      exporter.export(spans);
    } catch (RuntimeException ex) {
      log.warn("trace export failed for {}", id, ex);
    }
  }

  private String hex(int chars) {
    byte[] bytes = new byte[chars / 2];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private record Written(
      String body,
      String model,
      String billedModel,
      Integer inputTokens,
      Integer outputTokens,
      String promptName,
      Integer promptVersion,
      String guardrail) {}
}
