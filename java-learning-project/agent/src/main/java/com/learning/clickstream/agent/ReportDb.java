package com.learning.clickstream.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One SQLite file, one connection per call. {@link ReportStore} holds the lock.
 * The report row and its first assistant message commit in the same transaction.
 */
final class ReportDb {
  private final Path file;
  private final ReportRows rows = new ReportRows();

  ReportDb(Path file) {
    this.file = file;
    Path parent = file.getParent();
    if (parent != null) {
      try {
        Files.createDirectories(parent);
      } catch (IOException ex) {
        throw new UncheckedIOException("could not create " + parent, ex);
      }
    }
    call(c -> {
      rows.create(c);
      return null;
    });
  }

  void insert(
      String id,
      Instant createdAt,
      List<String> triggers,
      String snapshotJson,
      String body,
      String model,
      String traceId,
      String rootSpanId) {
    call(c -> {
      c.setAutoCommit(false);
      try {
        rows.writeReport(c, id, createdAt, triggers, snapshotJson, body, model, traceId, rootSpanId);
        rows.writeMessage(c, id, "assistant", body, createdAt);
        c.commit();
      } catch (SQLException ex) {
        c.rollback();
        throw ex;
      }
      return null;
    });
  }

  List<ReportSummary> list() {
    return call(rows::list);
  }

  Optional<StoredReport> find(String id) {
    return call(c -> rows.readReport(c, id));
  }

  /** 0 when the report id is unknown, so the caller writes nothing. */
  long append(String id, String role, String content, Instant createdAt) {
    return call(c -> {
      if (rows.readReport(c, id).isEmpty()) {
        return 0L;
      }
      return rows.writeMessage(c, id, role, content, createdAt);
    });
  }

  private <T> T call(Sql<T> work) {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
      return work.apply(c);
    } catch (SQLException ex) {
      throw new IllegalStateException("report store failed", ex);
    }
  }

  @FunctionalInterface
  private interface Sql<T> {
    T apply(Connection c) throws SQLException;
  }
}
