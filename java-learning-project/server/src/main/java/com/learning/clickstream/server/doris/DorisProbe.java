package com.learning.clickstream.server.doris;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

/**
 * Phase 2 exit check, shared by the main class and the test.
 *
 * Order matters. Ping proves 9030 is open. DDL creates the table. Stream load
 * proves 8030 and 8040. The SELECT proves the row is actually visible, which
 * a Success status alone does not guarantee if publish is still finishing.
 */
public final class DorisProbe {
  public String run(DorisConfig config) {
    DorisDataSource dataSource = new DorisDataSource(config);
    try (Connection connection = dataSource.open()) {
      ping(connection);
      new SchemaBootstrap().apply(connection);
    } catch (SQLException ex) {
      // close() throws SQLException. A failed close is still a failed probe.
      throw new DorisAccessException("Doris connection failed while creating the schema", ex);
    }
    String eventId = "probe-" + UUID.randomUUID();
    String line = jsonLine(eventId, Instant.now());
    new DorisStreamLoader(config).loadLines(line, "probe-" + UUID.randomUUID());
    String visible = awaitVisible(dataSource, eventId);
    if (visible == null) {
      throw new DorisAccessException("stream load succeeded but " + eventId + " was not visible after 10s");
    }
    return visible;
  }

  private static void ping(Connection connection) {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SELECT 1");
    } catch (SQLException ex) {
      throw new DorisAccessException("Doris JDBC ping failed. Expected the MySQL protocol on 9030.", ex);
    }
  }

  /**
   * Doris DATETIME has no zone. We store the UTC wall clock and the report
   * queries in phase 6 must use the same clock.
   */
  static String jsonLine(String eventId, Instant when) {
    String time = DorisTime.format(when);
    return "{"
        + "\"occurred_at\":\"" + time + "\","
        + "\"tenant_id\":\"phase2-probe\","
        + "\"event_id\":\"" + eventId + "\","
        + "\"user_id\":\"probe-user\","
        + "\"event_type\":\"page_view\","
        + "\"properties\":\"{}\","
        + "\"ingested_at\":\"" + time + "\""
        + "}";
  }

  private static String awaitVisible(DorisDataSource dataSource, String eventId) {
    Instant deadline = Instant.now().plusSeconds(10);
    while (true) {
      try (Connection connection = dataSource.open();
          PreparedStatement statement = connection.prepareStatement(
              "SELECT event_id FROM clickstream.events WHERE event_id = ?")) {
        statement.setString(1, eventId);
        try (ResultSet rows = statement.executeQuery()) {
          if (rows.next()) {
            return rows.getString(1);
          }
        }
      } catch (SQLException ex) {
        throw new DorisAccessException("could not read back " + eventId, ex);
      }
      if (Instant.now().isAfter(deadline)) {
        return null;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new DorisAccessException("interrupted while waiting for " + eventId, ex);
      }
    }
  }
}
