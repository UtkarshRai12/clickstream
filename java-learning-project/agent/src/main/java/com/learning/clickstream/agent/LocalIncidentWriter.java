package com.learning.clickstream.agent;

import java.util.Locale;
import java.util.Map;

/**
 * Report text with no model call. It restates the gate's sentences and the
 * snapshot counts, then the two limits of this hop: the edge already returned
 * 202, and this snapshot cannot see Doris or a single tenant.
 */
public final class LocalIncidentWriter implements IncidentWriter {
  @Override
  public String modelName() {
    return "local";
  }

  @Override
  public WriterResult writeReport(IncidentSnapshot snapshot) {
    return WriterResult.local("""
        The ingress hop crossed a hardcoded trigger.
        %s

        Queue depth %d. Attempts %d, failures %d, dropped %d. Mean Accept time %s seconds. Status codes: %s.

        The edge has already returned 202 for the batches it accepted. This snapshot cannot see Doris or a single tenant.
        """.formatted(
        reasons(snapshot),
        snapshot.queueDepth(),
        snapshot.attempts(),
        snapshot.failures(),
        snapshot.dropped(),
        mean(snapshot.meanSeconds()),
        codes(snapshot.errorsByCode())));
  }

  @Override
  public WriterResult answer(FollowUpQuery query) {
    // The question is not re-read from the edge. The stored snapshot is the evidence.
    IncidentSnapshot snapshot = query.snapshot();
    return WriterResult.local("""
        Using the stored snapshot only.
        Queue depth %d. Attempts %d, failures %d, dropped %d. Mean Accept time %s seconds. Status codes: %s.
        %s
        """.formatted(
        snapshot.queueDepth(),
        snapshot.attempts(),
        snapshot.failures(),
        snapshot.dropped(),
        mean(snapshot.meanSeconds()),
        codes(snapshot.errorsByCode()),
        reasons(snapshot)));
  }

  private static String reasons(IncidentSnapshot snapshot) {
    return String.join("\n", snapshot.reasons());
  }

  private static String mean(double seconds) {
    return String.format(Locale.ROOT, "%.2f", seconds);
  }

  private static String codes(Map<String, Integer> errors) {
    if (errors.isEmpty()) {
      return "none";
    }
    StringBuilder out = new StringBuilder();
    for (Map.Entry<String, Integer> entry : errors.entrySet()) {
      if (!out.isEmpty()) {
        out.append(", ");
      }
      out.append(entry.getKey()).append(' ').append(entry.getValue());
    }
    return out.toString();
  }
}
