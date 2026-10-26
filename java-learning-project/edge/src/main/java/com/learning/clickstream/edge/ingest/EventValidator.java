package com.learning.clickstream.edge.ingest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.config.ApplicationConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Batch rules that sit on top of parsing. An empty batch is 400.
 * A batch over the cap is 413 and is not queued, so Doris never sees it.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EventValidator {
  private final ApplicationConfig config;

  public void validateBatch(List<IncomingEvent> events) {
    if (events == null || events.isEmpty()) {
      throw new IngestRejection(400, "empty_batch", null, "events is empty");
    }
    if (events.size() > config.getIngestBatchCap()) {
      throw new IngestRejection(413, "batch_too_large", null, "max " + config.getIngestBatchCap());
    }
  }
}
