package com.learning.clickstream.edge.ingest;

import java.time.Instant;
import lombok.Value;

/**
 * One event after JSON parsing. tenant_id is not here.
 * The servlet copies the tenant from the JWT onto the queued batch.
 */
@Value
public class IncomingEvent {
  String eventId;
  String userId;
  String eventType;
  Instant occurredAt;
  String propertiesJson;
}
