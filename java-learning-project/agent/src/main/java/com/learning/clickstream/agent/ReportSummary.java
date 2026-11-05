package com.learning.clickstream.agent;

import java.time.Instant;
import java.util.List;

/** A row for the report list. The transcript is loaded only when a report is opened. */
public record ReportSummary(
    String id, Instant createdAt, List<String> triggers, String model, String preview) {}
