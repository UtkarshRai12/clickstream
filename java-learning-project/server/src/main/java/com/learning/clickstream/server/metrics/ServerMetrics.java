package com.learning.clickstream.server.metrics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Server-side metrics. Stream load time includes failed attempts.
 * The report histogram is registered now and stays empty until phase 6
 * calls {@link #observeReportQuery(double)}.
 */
@Singleton
@Slf4j
public final class ServerMetrics implements AutoCloseable {
  private final PrometheusRegistry registry = new PrometheusRegistry();
  private final Histogram streamLoadSeconds;
  private final Counter streamLoadFailures;
  private final Histogram reportQuerySeconds;
  private HTTPServer http;

  @Inject
  public ServerMetrics() {
    streamLoadSeconds = histogram(
        "clickstream_stream_load_seconds",
        "Seconds spent in one Doris stream load, success or failure.");
    streamLoadFailures = Counter.builder()
        .name("clickstream_stream_load_failures_total")
        .help("Stream loads that threw. The rows were put back on the buffer.")
        .build();
    // No observations yet. Phase 6 times the Doris report query with this histogram.
    reportQuerySeconds = histogram(
        "clickstream_report_query_seconds",
        "Seconds spent in a Doris report query. Unused until phase 6.");
    registry.register(streamLoadSeconds);
    registry.register(streamLoadFailures);
    registry.register(reportQuerySeconds);
  }

  /** Phase 6 calls this around the report SQL. Nothing calls it today. */
  public void observeReportQuery(double seconds) {
    reportQuerySeconds.observe(seconds);
  }

  public void recordStreamLoad(long startedNanos, boolean success) {
    double seconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
    streamLoadSeconds.observe(seconds);
    if (!success) {
      streamLoadFailures.inc();
    }
  }

  /** Binds 127.0.0.1 only. Docker reaches it through the host network, not a published port. */
  public void start(int port) {
    try {
      http = HTTPServer.builder()
          .hostname("127.0.0.1")
          .port(port)
          .registry(registry)
          .buildAndStart();
    } catch (IOException ex) {
      throw new IllegalStateException("server metrics port " + port + " failed", ex);
    }
    log.info("server metrics on http://127.0.0.1:{}/metrics", http.getPort());
  }

  public int port() {
    return http.getPort();
  }

  /**
   * The client stores a counter as {@code name} and adds {@code _total} only in the
   * text Prometheus scrapes. Match either spelling.
   */
  public boolean exposes(String name) {
    String withoutTotal = name.endsWith("_total") ? name.substring(0, name.length() - 6) : name;
    for (MetricSnapshot snapshot : registry.scrape()) {
      String prometheusName = snapshot.getMetadata().getPrometheusName();
      if (name.equals(prometheusName) || withoutTotal.equals(prometheusName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() {
    if (http != null) {
      http.close();
      http = null;
    }
  }

  private static Histogram histogram(String name, String help) {
    return Histogram.builder()
        .name(name)
        .help(help)
        // Classic buckets so Grafana can draw a heatmap without native histograms.
        .classicOnly()
        .classicUpperBounds(0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10)
        .build();
  }
}
