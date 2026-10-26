package com.learning.clickstream.edge.ingest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.auth.Caller;
import com.learning.clickstream.edge.http.HttpResponses;
import com.learning.clickstream.edge.metrics.EdgeMetrics;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Queues a validated batch and returns 202. Doris has not seen the rows yet.
 * The dispatcher thread sends them to the server after this method returns.
 * A full queue is 503, which means the collector should retry. It is not a 202.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class IngestAcceptance {
  private final EventValidator validator;
  private final AcceptedEventQueue queue;
  private final EdgeMetrics metrics;

  public void accept(Caller caller, String requestId, HttpServletResponse response, List<IncomingEvent> events)
      throws IOException {
    validator.validateBatch(events);
    String id = requestId == null || requestId.isBlank() ? "missing" : requestId;
    boolean queued = queue.offer(new QueuedBatch(caller.getTenantId(), id, List.copyOf(events)));
    if (!queued) {
      log.warn("ingest queue full tenant={} count={}", caller.getTenantId(), events.size());
      HttpResponses.error(response, 503, "queue_full");
      return;
    }
    log.info("accepted events count={} tenant={} requestId={}", events.size(), caller.getTenantId(), id);
    // Count events, not HTTP calls. A batch of 10 is ten, and the filter skips 202.
    metrics.accepted(events.size());
    HttpResponses.json(response, 202, "{\"accepted\":" + events.size() + "}");
  }
}
