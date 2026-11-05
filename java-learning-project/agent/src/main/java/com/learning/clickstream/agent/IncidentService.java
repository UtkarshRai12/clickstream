package com.learning.clickstream.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
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
  /** One stored turn. Longer model output is cut so a row cannot grow without a bound. */
  static final int BODY_CAP = 16_000;

  /** A later turn can drift. The writer sees this many recent messages, plus the new question. */
  static final int HISTORY = 20;

  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
  private static final ObjectMapper JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final ReportStore store;
  private final IncidentWriter writer;
  private final LocalIncidentWriter local = new LocalIncidentWriter();
  private final TraceExporter exporter;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public IncidentService(ReportStore store, IncidentWriter writer, TraceExporter exporter, Clock clock) {
    this.store = store;
    this.writer = writer;
    this.exporter = exporter;
    this.clock = clock;
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
    Written written = write(snapshot);
    String id = UUID.randomUUID().toString();
    String traceId = hex(32);
    String rootSpanId = hex(16);
    String snapshotJson = toJson(snapshot);
    StoredReport report = store.insert(
        id, clock.instant(), snapshot.reasons(), snapshotJson, written.body, written.model, traceId, rootSpanId);
    String prompt = prompt(snapshotJson, null);
    export(id, List.of(
        Observation.agent(traceId, rootSpanId, id, written.model, snapshotJson, written.body),
        Observation.generation(
            traceId, hex(16), rootSpanId, id, Observation.WRITE_REPORT, written.model, prompt, written.body)));
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
    IncidentSnapshot snapshot = fromJson(report.snapshotJson());
    FollowUpQuery query = new FollowUpQuery(snapshot, report.body(), last(report.messages()), question);
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
        written.model,
        prompt(report.snapshotJson(), question),
        written.body)));
    return Optional.of(assistant);
  }

  private Written write(IncidentSnapshot snapshot) {
    try {
      return new Written(cap(writer.writeReport(snapshot)), writer.modelName());
    } catch (RuntimeException ex) {
      // A local failure is the last resort, so it propagates and nothing is stored.
      if (writer instanceof LocalIncidentWriter) {
        throw ex;
      }
      log.warn("incident writer failed, storing the local report", ex);
      return new Written(cap(local.writeReport(snapshot)), local.modelName());
    }
  }

  private Written answer(FollowUpQuery query) {
    try {
      return new Written(cap(writer.answer(query)), writer.modelName());
    } catch (RuntimeException ex) {
      if (writer instanceof LocalIncidentWriter) {
        throw ex;
      }
      log.warn("follow-up writer failed, storing the local answer", ex);
      return new Written(cap(local.answer(query)), local.modelName());
    }
  }

  /** The export runs after the commit. A sink that throws leaves the row in place. */
  private void export(String id, List<Observation> spans) {
    try {
      exporter.export(spans);
    } catch (RuntimeException ex) {
      log.warn("trace export failed for {}", id, ex);
    }
  }

  private static List<ChatMessage> last(List<ChatMessage> messages) {
    if (messages.size() <= HISTORY) {
      return messages;
    }
    return List.copyOf(messages.subList(messages.size() - HISTORY, messages.size()));
  }

  /** The same rule a model would be given. The local writer does not call one. */
  private static String prompt(String snapshotJson, String question) {
    String text = "Use only these numbers. Do not invent a cause.\n" + snapshotJson;
    if (question == null) {
      return text;
    }
    return text + "\nQuestion: " + question;
  }

  static String cap(String body) {
    return body.length() <= BODY_CAP ? body : body.substring(0, BODY_CAP);
  }

  private String hex(int chars) {
    byte[] bytes = new byte[chars / 2];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String toJson(IncidentSnapshot snapshot) {
    try {
      return JSON.writeValueAsString(snapshot);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static IncidentSnapshot fromJson(String raw) {
    try {
      return JSON.readValue(raw, IncidentSnapshot.class);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private record Written(String body, String model) {}
}
