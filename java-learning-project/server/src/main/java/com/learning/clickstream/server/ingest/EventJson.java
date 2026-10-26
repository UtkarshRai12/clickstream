package com.learning.clickstream.server.ingest;

import com.learning.clickstream.server.doris.DorisTime;
import java.time.Instant;
import java.util.List;

/** Builds one JSON object per line. Doris stream load reads that shape by column name. */
public final class EventJson {
  private EventJson() {}

  public static String lines(List<BufferedEvent> events, Instant ingestedAt) {
    String ingested = DorisTime.format(ingestedAt);
    StringBuilder body = new StringBuilder();
    for (BufferedEvent event : events) {
      if (!body.isEmpty()) {
        body.append('\n');
      }
      body.append('{')
          .append("\"occurred_at\":").append(quote(DorisTime.format(event.getOccurredAt()))).append(',')
          .append("\"tenant_id\":").append(quote(event.getTenantId())).append(',')
          .append("\"event_id\":").append(quote(event.getEventId())).append(',')
          .append("\"user_id\":").append(quote(event.getUserId())).append(',')
          .append("\"event_type\":").append(quote(event.getEventType())).append(',')
          .append("\"properties\":").append(quote(event.getPropertiesJson())).append(',')
          .append("\"ingested_at\":").append(quote(ingested))
          .append('}');
    }
    return body.toString();
  }

  private static String quote(String value) {
    String safe = value == null ? "" : value;
    return "\"" + safe
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        + "\"";
  }
}
