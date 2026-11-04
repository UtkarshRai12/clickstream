package com.learning.clickstream.edge.ingest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.metrics.EdgeMetrics;
import io.grpc.Status;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Drains the accept queue on one background thread.
 * A lost connection is retried. A batch the server calls invalid is dropped,
 * because sending it again will fail the same way.
 * A lost ack can write the same events twice. The Doris table is DUPLICATE KEY,
 * so those rows would both remain.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class IngestDispatcher implements AutoCloseable {
  private static final int ATTEMPTS = 3;

  private final AcceptedEventQueue queue;
  private final IngestClient client;
  private final EdgeMetrics metrics;
  private final HopGate gate;
  private volatile boolean running;
  private Thread thread;

  public void start() {
    running = true;
    thread = new Thread(this::loop, "ingest-dispatcher");
    thread.setDaemon(true);
    thread.start();
  }

  private void loop() {
    while (running) {
      try {
        sendWithRetry(queue.take());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void sendWithRetry(QueuedBatch batch) {
    // This thread is not the HTTP thread, so the filter's MDC is not visible here.
    MDC.put("request_id", batch.getRequestId());
    MDC.put("tenant_id", batch.getTenantId());
    MDC.put("route", "IngestService/Accept");
    try {
      for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
        long started = System.nanoTime();
        try {
          client.accept(batch);
          // The ack means the server queued the rows. Doris may still not have them.
          metrics.recordForward(started, true, "OK");
          note(gate.onForward(true, "OK", secondsSince(started), queue.size()));
          log.info("forwarded events count={} tenant={}", batch.getEvents().size(), batch.getTenantId());
          return;
        } catch (RuntimeException ex) {
          Status.Code code = Status.fromThrowable(ex).getCode();
          metrics.recordForward(started, false, code.name());
          double seconds = secondsSince(started);
          Optional<Snapshot> risen = gate.onForward(false, code.name(), seconds, queue.size());
          if (code == Status.Code.INVALID_ARGUMENT || code == Status.Code.UNAUTHENTICATED) {
            metrics.recordDropped();
            note(one(risen, gate.onDrop(queue.size())));
            log.error("dropping batch count={} status={}", batch.getEvents().size(), code);
            return;
          }
          log.warn("ingest send failed attempt={} status={}", attempt, code);
          if (attempt == ATTEMPTS || !sleep(attempt)) {
            metrics.recordDropped();
            note(one(risen, gate.onDrop(queue.size())));
            log.error("dropping batch after retries count={} requestId={}",
                batch.getEvents().size(), batch.getRequestId());
            return;
          }
          note(risen);
        }
      }
    } finally {
      MDC.remove("request_id");
      MDC.remove("tenant_id");
      MDC.remove("route");
    }
  }

  /** Part 2 returns the snapshot. Nothing posts it until the agent exists. */
  private void note(Optional<Snapshot> snapshot) {
    snapshot.ifPresent(s -> log.warn("ingress threshold crossed reasons={}", s.reasons()));
  }

  /** A drop and another signal on the same call are one snapshot. */
  private static Optional<Snapshot> one(Optional<Snapshot> first, Optional<Snapshot> second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty()) {
      return first;
    }
    return Optional.of(first.get().plus(second.get()));
  }

  private static double secondsSince(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000_000.0;
  }

  private static boolean sleep(int attempt) {
    try {
      Thread.sleep(100L * attempt);
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
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
  }
}
