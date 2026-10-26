package com.learning.clickstream.edge.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptedEventQueueTest {

  @Test
  void aFullQueueRefusesTheNextBatch() {
    AcceptedEventQueue queue = new AcceptedEventQueue(1);
    QueuedBatch batch = new QueuedBatch("tenant-a", "req", List.of(
        new IncomingEvent("e1", "ada", "click", Instant.parse("2026-10-24T12:00:00Z"), "{}")));

    assertTrue(queue.offer(batch));
    assertFalse(queue.offer(batch));
  }
}
