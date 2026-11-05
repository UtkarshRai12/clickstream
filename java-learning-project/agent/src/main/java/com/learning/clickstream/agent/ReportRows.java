package com.learning.clickstream.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The two tables and the mapping between a row and a record. */
final class ReportRows {
  private static final int PREVIEW = 140;
  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  private final ObjectMapper json = new ObjectMapper();

  void create(Connection c) throws SQLException {
    try (Statement s = c.createStatement()) {
      // id is also the Langfuse session id once export is wired.
      s.execute("""
          create table if not exists reports (
            id text primary key,
            created_at text not null,
            triggers text not null,
            snapshot_json text not null,
            body text not null,
            model text not null,
            trace_id text not null,
            root_span_id text not null
          )
          """);
      s.execute("""
          create table if not exists messages (
            id integer primary key autoincrement,
            report_id text not null,
            role text not null,
            content text not null,
            created_at text not null
          )
          """);
    }
  }

  void writeReport(
      Connection c, String id, Instant createdAt, List<String> triggers, String snapshotJson,
      String body, String model, String traceId, String rootSpanId) throws SQLException {
    String sql = "insert into reports (id, created_at, triggers, snapshot_json, body, model, "
        + "trace_id, root_span_id) values (?, ?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      ps.setString(2, createdAt.toString());
      ps.setString(3, writeTriggers(triggers));
      ps.setString(4, snapshotJson);
      ps.setString(5, body);
      ps.setString(6, model);
      ps.setString(7, traceId);
      ps.setString(8, rootSpanId);
      ps.executeUpdate();
    }
  }

  long writeMessage(Connection c, String id, String role, String content, Instant createdAt)
      throws SQLException {
    String sql = "insert into messages (report_id, role, content, created_at) values (?, ?, ?, ?)";
    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, id);
      ps.setString(2, role);
      ps.setString(3, content);
      ps.setString(4, createdAt.toString());
      ps.executeUpdate();
      try (ResultSet keys = ps.getGeneratedKeys()) {
        keys.next();
        return keys.getLong(1);
      }
    }
  }

  List<ReportSummary> list(Connection c) throws SQLException {
    String sql = "select id, created_at, triggers, model, body from reports "
        + "order by created_at desc, id desc";
    try (Statement s = c.createStatement(); ResultSet rows = s.executeQuery(sql)) {
      List<ReportSummary> summaries = new ArrayList<>();
      while (rows.next()) {
        summaries.add(new ReportSummary(
            rows.getString("id"),
            Instant.parse(rows.getString("created_at")),
            readTriggers(rows.getString("triggers")),
            rows.getString("model"),
            preview(rows.getString("body"))));
      }
      return summaries;
    }
  }

  Optional<StoredReport> readReport(Connection c, String id) throws SQLException {
    String sql = "select created_at, triggers, snapshot_json, body, model, trace_id, root_span_id "
        + "from reports where id = ?";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (ResultSet row = ps.executeQuery()) {
        if (!row.next()) {
          return Optional.empty();
        }
        return Optional.of(new StoredReport(
            id,
            Instant.parse(row.getString("created_at")),
            readTriggers(row.getString("triggers")),
            row.getString("snapshot_json"),
            row.getString("body"),
            row.getString("model"),
            row.getString("trace_id"),
            row.getString("root_span_id"),
            messages(c, id)));
      }
    }
  }

  private List<ChatMessage> messages(Connection c, String id) throws SQLException {
    String sql = "select id, role, content, created_at from messages where report_id = ? order by id";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (ResultSet rows = ps.executeQuery()) {
        List<ChatMessage> messages = new ArrayList<>();
        while (rows.next()) {
          messages.add(new ChatMessage(
              rows.getLong("id"),
              rows.getString("role"),
              rows.getString("content"),
              Instant.parse(rows.getString("created_at"))));
        }
        return List.copyOf(messages);
      }
    }
  }

  /** First line of the body, cut at 140 characters. The full text stays on the row. */
  static String preview(String body) {
    int newline = body.indexOf('\n');
    String line = newline < 0 ? body : body.substring(0, newline);
    return line.length() <= PREVIEW ? line : line.substring(0, PREVIEW);
  }

  private String writeTriggers(List<String> triggers) {
    try {
      return json.writeValueAsString(triggers);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private List<String> readTriggers(String raw) {
    try {
      return json.readValue(raw, STRINGS);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
