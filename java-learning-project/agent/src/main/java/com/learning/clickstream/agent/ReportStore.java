package com.learning.clickstream.agent;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The only way a report is read or written. Methods are synchronized because a
 * few threads share one file and the volume is one write per crossing.
 */
public final class ReportStore {
  private final ReportDb db;

  public ReportStore(Path file) {
    db = new ReportDb(file);
  }

  /**
   * Writes the report and copies {@code body} into the first assistant message.
   * Both rows commit together, so a list entry always has a transcript.
   */
  public synchronized StoredReport insert(
      String id,
      Instant createdAt,
      List<String> triggers,
      String snapshotJson,
      String body,
      String model,
      String traceId,
      String rootSpanId) {
    db.insert(id, createdAt, triggers, snapshotJson, body, model, traceId, rootSpanId);
    return find(id).orElseThrow();
  }

  /** Newest first. Messages stay unloaded until the report is opened. */
  public synchronized List<ReportSummary> list() {
    return db.list();
  }

  /** The report and its messages, oldest turn first. Empty when the id is unknown. */
  public synchronized Optional<StoredReport> find(String id) {
    return db.find(id);
  }

  /** Adds a turn. Empty when the report does not exist, and no row is written. */
  public synchronized Optional<ChatMessage> append(String id, String role, String content, Instant createdAt) {
    long messageId = db.append(id, role, content, createdAt);
    if (messageId == 0) {
      return Optional.empty();
    }
    return Optional.of(new ChatMessage(messageId, role, content, createdAt));
  }
}
