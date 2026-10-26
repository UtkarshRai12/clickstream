package com.learning.clickstream.edge.ingest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * In-memory handoff from the HTTP thread to the dispatcher thread.
 * offer returns false when the queue is full. The servlet turns that into 503.
 * A full queue is not a 202. The collector can retry.
 * Shutdown can drop batches that are still sitting here.
 */
public final class AcceptedEventQueue {
  private final BlockingQueue<QueuedBatch> batches;

  public AcceptedEventQueue(int capacity) {
    this.batches = new ArrayBlockingQueue<>(capacity);
  }

  public boolean offer(QueuedBatch batch) {
    return batches.offer(batch);
  }

  /** Batches still waiting. The Prometheus gauge reads this when it is scraped. */
  public int size() {
    return batches.size();
  }

  public QueuedBatch take() throws InterruptedException {
    return batches.take();
  }
}
