package com.learning.clickstream.server.doris;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Creates the database and the events table if they are missing.
 *
 * DUPLICATE KEY keeps every event. Clickstream rows are facts, not entities
 * we update in place. The key columns are the sort order inside a tablet,
 * and they have to be the leading columns. occurred_at is first because the
 * auto partition expression is date_trunc of that column.
 *
 * AUTO PARTITION creates a daily partition on the first row for that day.
 * We do not pre-create a partition list. replication_num is 1 because the
 * all-in-one container is a single Backend.
 */
public final class SchemaBootstrap {
  public static final String DATABASE = "clickstream";
  public static final String TABLE = "events";

  public List<String> statements() {
    return List.of(
        "CREATE DATABASE IF NOT EXISTS " + DATABASE,
        """
        CREATE TABLE IF NOT EXISTS clickstream.events (
          occurred_at DATETIME NOT NULL,
          tenant_id VARCHAR(64) NOT NULL,
          event_id VARCHAR(64) NOT NULL,
          user_id VARCHAR(64) NOT NULL,
          event_type VARCHAR(64) NOT NULL,
          properties STRING NULL,
          ingested_at DATETIME NOT NULL
        )
        DUPLICATE KEY(occurred_at, tenant_id, event_id)
        AUTO PARTITION BY RANGE (date_trunc(occurred_at, 'day'))
        ()
        DISTRIBUTED BY HASH(user_id) BUCKETS 8
        PROPERTIES (
          "replication_num" = "1"
        )
        """);
  }

  public void apply(Connection connection) {
    for (String sql : statements()) {
      try (Statement statement = connection.createStatement()) {
        statement.execute(sql);
      } catch (SQLException ex) {
        throw new DorisAccessException("Doris DDL failed:\n" + sql, ex);
      }
    }
  }
}
