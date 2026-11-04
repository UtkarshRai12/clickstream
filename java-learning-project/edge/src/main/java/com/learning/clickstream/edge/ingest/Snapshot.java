package com.learning.clickstream.edge.ingest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The numbers at the moment a trigger rose. This is the evidence a report is
 * allowed to use. {@code dropped} is 1 only on the call that abandoned a batch.
 */
public record Snapshot(
    Instant occurredAt,
    int queueDepth,
    int attempts,
    int failures,
    int dropped,
    double meanSeconds,
    Map<String, Integer> errorsByCode,
    List<String> reasons) {

  public Snapshot {
    errorsByCode = Map.copyOf(errorsByCode);
    reasons = List.copyOf(reasons);
  }

  /**
   * One dispatcher call can raise two signals through two methods.
   * The later snapshot has the drop bit. The reasons of both stay.
   */
  public Snapshot plus(Snapshot later) {
    List<String> combined = new ArrayList<>(reasons);
    for (String reason : later.reasons) {
      if (!combined.contains(reason)) {
        combined.add(reason);
      }
    }
    return new Snapshot(
        later.occurredAt,
        later.queueDepth,
        later.attempts,
        later.failures,
        Math.max(dropped, later.dropped),
        later.meanSeconds,
        later.errorsByCode,
        combined);
  }
}
