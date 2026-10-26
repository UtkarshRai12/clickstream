package com.learning.clickstream.server.doris;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaBootstrapTest {

  @Test
  void ddlIsADuplicateKeyEventsTable() {
    String sql = String.join("\n", new SchemaBootstrap().statements());
    assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS clickstream"));
    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS clickstream.events"));
    assertTrue(sql.contains("DUPLICATE KEY(occurred_at, tenant_id, event_id)"));
    assertTrue(sql.contains("AUTO PARTITION BY RANGE (date_trunc(occurred_at, 'day'))"));
  }
}
