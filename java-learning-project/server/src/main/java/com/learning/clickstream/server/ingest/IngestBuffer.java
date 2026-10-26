package com.learning.clickstream.server.ingest;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Rows the gRPC handler has accepted and the flusher has not loaded yet.
 * add and drain share one lock. The stream load itself runs outside the lock.
 */
@Singleton
public final class IngestBuffer {
  private final List<BufferedEvent> pending = new ArrayList<>();

  public synchronized void add(List<BufferedEvent> events) {
    pending.addAll(events);
    if (pending.size() > 10_000) {
      // The flusher is behind. Keep the rows. The log is the signal to look at Doris.
      // Dropping them would make a 202 lie about data that then vanished.
    }
  }

  public synchronized int size() {
    return pending.size();
  }

  public synchronized List<BufferedEvent> drain() {
    if (pending.isEmpty()) {
      return List.of();
    }
    List<BufferedEvent> copy = List.copyOf(pending);
    pending.clear();
    return copy;
  }

  /** Puts a failed load back in front so the next tick retries those rows first. */
  public synchronized void restore(List<BufferedEvent> events) {
    pending.addAll(0, events);
  }
}
