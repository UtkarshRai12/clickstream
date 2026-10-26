package com.learning.clickstream.server.doris;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens one JDBC connection per call. There is no pool yet.
 * Phase 6 can add one when report queries are frequent. Callers close the
 * connection with try-with-resources.
 *
 * The URL has no database name. SchemaBootstrap creates `clickstream`, and
 * later statements use the qualified name clickstream.events.
 */
public final class DorisDataSource {
  private final DorisConfig config;

  public DorisDataSource(DorisConfig config) {
    this.config = config;
  }

  public Connection open() {
    try {
      return DriverManager.getConnection(config.getJdbcUrl(), config.getUser(), config.getPassword());
    } catch (SQLException ex) {
      throw new DorisAccessException(
          "Doris JDBC connection failed for " + config.getJdbcUrl()
              + ". The container should publish 9030.",
          ex);
    }
  }
}
