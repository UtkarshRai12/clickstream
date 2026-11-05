package com.learning.clickstream.edge.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.edge.metrics.EdgeMetrics;
import io.grpc.Status;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The Accept RPC is visible on the edge scrape port, including a dropped batch. */
class HopMetricsTest {
  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void unavailableBatchIsCountedThenDropped() throws Exception {
    AcceptedEventQueue queue = new AcceptedEventQueue(2);
    EdgeMetrics metrics = new EdgeMetrics(queue);
    AtomicInteger calls = new AtomicInteger();
    IngestClient client = batch -> {
      calls.incrementAndGet();
      throw Status.UNAVAILABLE.withDescription("down").asRuntimeException();
    };
    // The drop crosses a trigger. Point the notifier at a closed port so the test does not wait on 8095.
    IngestDispatcher dispatcher = new IngestDispatcher(
        queue, client, metrics, new HopGate(Clock.systemUTC()),
        new AgentNotifier(URI.create("http://127.0.0.1:9/api/incidents")));
    queue.offer(new QueuedBatch("tenant-a", "req-1", List.of(event())));
    dispatcher.start();
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
      while (calls.get() < 3 && System.nanoTime() < deadline) {
        Thread.sleep(20);
      }
    } finally {
      dispatcher.close();
    }
    assertEquals(3, calls.get());

    metrics.start(0);
    try {
      String body = scrape(metrics);
      assertTrue(body.contains("clickstream_grpc_forward_total"), body);
      assertTrue(body.contains("UNAVAILABLE"), body);
      assertTrue(body.contains("clickstream_grpc_batches_dropped_total"), body);
      assertTrue(body.contains("clickstream_grpc_forward_seconds"), body);
    } finally {
      metrics.close();
    }
  }

  @Test
  void scrapeShowsOkAndError() throws Exception {
    EdgeMetrics metrics = new EdgeMetrics(new AcceptedEventQueue(1));
    metrics.recordForward(System.nanoTime(), true, "OK");
    metrics.recordForward(System.nanoTime(), false, "DEADLINE_EXCEEDED");
    metrics.start(0);
    try {
      String body = scrape(metrics);
      assertTrue(body.contains("result=\"ok\""), body);
      assertTrue(body.contains("result=\"error\""), body);
    } finally {
      metrics.close();
    }
  }

  private String scrape(EdgeMetrics metrics) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + metrics.port() + "/metrics"))
        .timeout(Duration.ofSeconds(2))
        .GET()
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return response.body();
  }

  private static IncomingEvent event() {
    return new IncomingEvent("e1", "ada", "click", Instant.parse("2026-10-24T12:00:00Z"), "{}");
  }
}
