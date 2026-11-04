package com.learning.clickstream.edge.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.edge.ingest.AcceptedEventQueue;
import com.learning.clickstream.edge.ingest.IncomingEvent;
import com.learning.clickstream.edge.ingest.QueuedBatch;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The registry publishes the phase 7 names without opening a scrape port. */
class EdgeMetricsTest {

  @Test
  void registryHasTheEdgeSeries() {
    AcceptedEventQueue queue = new AcceptedEventQueue(2);
    queue.offer(new QueuedBatch("tenant-a", "req-1", List.of(event())));
    EdgeMetrics metrics = new EdgeMetrics(queue);

    metrics.accepted(3);
    metrics.rejected(401);
    metrics.tokenIssued();
    metrics.tokenRejected(400);

    assertTrue(metrics.exposes("clickstream_events_accepted_total"));
    assertTrue(metrics.exposes("clickstream_events_rejected_total"));
    assertTrue(metrics.exposes("clickstream_tokens_issued_total"));
    assertTrue(metrics.exposes("clickstream_token_rejections_total"));
    assertTrue(metrics.exposes("clickstream_ingest_queue_depth"));
    assertTrue(metrics.exposes("clickstream_grpc_forward_seconds"));
    assertTrue(metrics.exposes("clickstream_grpc_forward_total"));
    assertTrue(metrics.exposes("clickstream_grpc_batches_dropped_total"));
    assertEquals(1, queue.size());
  }

  private static IncomingEvent event() {
    return new IncomingEvent("e1", "ada", "click", Instant.parse("2026-10-24T12:00:00Z"), "{}");
  }
}
