package com.learning.clickstream.edge.metrics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.ingest.AcceptedEventQueue;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Counters for the public HTTP edge, plus the Accept RPC to the ingest server.
 * The registry is private to this process start, so a second test in the same
 * JVM does not collide on the metric name.
 *
 * The scrape port is a separate HTTP server. It is not a servlet, so the JWT
 * filter cannot sit in front of Prometheus.
 */
@Singleton
@Slf4j
public final class EdgeMetrics implements AutoCloseable {
  private final PrometheusRegistry registry = new PrometheusRegistry();
  private final Counter accepted;
  private final Counter rejected;
  private final Counter tokensIssued;
  private final Counter tokenRejected;
  private final Histogram grpcForwardSeconds;
  private final Counter grpcForward;
  private final Counter grpcDropped;
  private HTTPServer http;

  @Inject
  public EdgeMetrics(AcceptedEventQueue queue) {
    // 202 means queued, not stored. The count is events, not HTTP calls.
    accepted = Counter.builder()
        .name("clickstream_events_accepted_total")
        .help("Events the edge queued with HTTP 202. Doris has not stored them yet.")
        .build();
    rejected = Counter.builder()
        .name("clickstream_events_rejected_total")
        .help("Ingest calls rejected before queueing. The label is the HTTP status.")
        .labelNames("code")
        .build();
    tokensIssued = Counter.builder()
        .name("clickstream_tokens_issued_total")
        .help("API keys exchanged for a JWT.")
        .build();
    tokenRejected = Counter.builder()
        .name("clickstream_token_rejections_total")
        .help("POST /v1/token calls that did not return a JWT. The label is the HTTP status.")
        .labelNames("code")
        .build();
    // Same bounds as the server stream-load histogram, so one Grafana heatmap style fits both.
    grpcForwardSeconds = histogram(
        "clickstream_grpc_forward_seconds",
        "Seconds spent in one Accept RPC, success or failure.");
    grpcForward = Counter.builder()
        .name("clickstream_grpc_forward_total")
        .help("Accept RPCs the edge finished. result is ok or error. code is the gRPC status.")
        .labelNames("result", "code")
        .build();
    grpcDropped = Counter.builder()
        .name("clickstream_grpc_batches_dropped_total")
        .help("Batches the dispatcher gave up on. These events are not sent again.")
        .build();
    // Read at scrape time so the number matches the queue, not a stale copy.
    GaugeWithCallback.builder()
        .name("clickstream_ingest_queue_depth")
        .help("Batches waiting for the dispatcher thread.")
        .callback(callback -> callback.call(queue.size()))
        .register(registry);
    registry.register(accepted);
    registry.register(rejected);
    registry.register(tokensIssued);
    registry.register(tokenRejected);
    registry.register(grpcForwardSeconds);
    registry.register(grpcForward);
    registry.register(grpcDropped);
  }

  /**
   * One finished Accept call, including a retry and a failure.
   * The dispatcher starts the clock immediately before {@code client.accept}.
   */
  public void recordForward(long startedNanos, boolean ok, String code) {
    double seconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
    grpcForwardSeconds.observe(seconds);
    grpcForward.labelValues(ok ? "ok" : "error", code).inc();
  }

  /** One abandoned batch. The failed tries were already counted by {@link #recordForward}. */
  public void recordDropped() {
    grpcDropped.inc();
  }

  public void accepted(int events) {
    accepted.inc(events);
  }

  public void rejected(int status) {
    rejected.labelValues(Integer.toString(status)).inc();
  }

  public void tokenIssued() {
    tokensIssued.inc();
  }

  public void tokenRejected(int status) {
    tokenRejected.labelValues(Integer.toString(status)).inc();
  }

  /** Binds 127.0.0.1 only. Port 0 lets the OS pick a free port. */
  public void start(int port) {
    try {
      http = HTTPServer.builder()
          .hostname("127.0.0.1")
          .port(port)
          .registry(registry)
          .buildAndStart();
    } catch (IOException ex) {
      throw new IllegalStateException("edge metrics port " + port + " failed", ex);
    }
    log.info("edge metrics on http://127.0.0.1:{}/metrics", http.getPort());
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

  private static Histogram histogram(String name, String help) {
    return Histogram.builder()
        .name(name)
        .help(help)
        .classicOnly()
        .classicUpperBounds(0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10)
        .build();
  }

  @Override
  public void close() {
    if (http != null) {
      http.close();
      http = null;
    }
  }
}
