package com.learning.clickstream.server.ingest;

import java.time.Instant;
import lombok.Value;

/** One row waiting for the next Doris stream load. */
@Value
public class BufferedEvent {
  String tenantId;
  String eventId;
  String userId;
  String eventType;
  Instant occurredAt;
  String propertiesJson;
}
