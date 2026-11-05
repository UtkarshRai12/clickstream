package com.learning.clickstream.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The numbers the edge posts when a trigger rises. Field names match the edge
 * {@code Snapshot} record, so the next phase can deserialize that POST body
 * without the agent module depending on the edge.
 */
public record IncidentSnapshot(
    Instant occurredAt,
    int queueDepth,
    int attempts,
    int failures,
    int dropped,
    double meanSeconds,
    Map<String, Integer> errorsByCode,
    List<String> reasons) {

  public IncidentSnapshot {
    errorsByCode = Map.copyOf(errorsByCode);
    reasons = List.copyOf(reasons);
  }
}
