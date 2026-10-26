package com.learning.clickstream.server.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Names only. The report histogram has no observations until phase 6. */
class ServerMetricsTest {

  @Test
  void registryHasTheServerSeries() {
    ServerMetrics metrics = new ServerMetrics();
    metrics.recordStreamLoad(System.nanoTime(), false);

    assertTrue(metrics.exposes("clickstream_stream_load_seconds"));
    assertTrue(metrics.exposes("clickstream_stream_load_failures_total"));
    assertTrue(metrics.exposes("clickstream_report_query_seconds"));
  }
}
