package com.learning.clickstream.server.ingest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.server.ServerConfig;
import com.learning.clickstream.server.doris.DorisStreamLoader;
import com.learning.clickstream.server.metrics.ServerMetrics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Moves queued rows into Doris on a timer. The gRPC ack happens before this runs.
 * A failed load puts the rows back. The next tick tries again with a new label.
 * If the failure happened after Doris committed, a retry can store the rows twice.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class IngestFlusher implements AutoCloseable {
  private final IngestBuffer buffer;
  private final DorisStreamLoader loader;
  private final ServerConfig config;
  private final ServerMetrics metrics;
  private volatile boolean running;
  private Thread thread;

  public void start() {
    running = true;
    thread = new Thread(this::loop, "ingest-flusher");
    thread.setDaemon(true);
    thread.start();
  }

  private void loop() {
    long nextFlush = System.currentTimeMillis() + config.getFlushIntervalMs();
    while (running) {
      try {
        Thread.sleep(50);
        // A full buffer flushes early. Otherwise the timer moves small batches.
        if (buffer.size() >= config.getFlushSize() || System.currentTimeMillis() >= nextFlush) {
          try {
            flushOnce();
          } catch (RuntimeException ignored) {
            // flushOnce already logged and put the rows back.
          }
          nextFlush = System.currentTimeMillis() + config.getFlushIntervalMs();
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  public synchronized void flushOnce() {
    List<BufferedEvent> rows = buffer.drain();
    if (rows.isEmpty()) {
      return;
    }
    String label = "ingest-" + UUID.randomUUID();
    long started = System.nanoTime();
    try {
      loader.loadLines(EventJson.lines(rows, Instant.now()), label);
      metrics.recordStreamLoad(started, true);
      log.info("loaded events count={} label={}", rows.size(), label);
    } catch (RuntimeException ex) {
      buffer.restore(rows);
      metrics.recordStreamLoad(started, false);
      // The next attempt waits for the timer. A tight loop would stamp the log
      // while Doris or the Expect header is unhappy.
      log.warn("stream load failed count={}: {}", rows.size(), ex.getMessage());
      log.debug("stream load failed", ex);
      throw ex;
    }
  }

  @Override
  public void close() {
    running = false;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(2000);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    flushOnce();
  }
}
