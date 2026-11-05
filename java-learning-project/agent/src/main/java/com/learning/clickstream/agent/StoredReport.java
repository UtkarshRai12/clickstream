package com.learning.clickstream.agent;

import java.time.Instant;
import java.util.List;

/**
 * One crossing and its transcript. {@code snapshotJson} is the evidence the
 * first answer used, and every follow-up reads it again from this row.
 */
public record StoredReport(
    String id,
    Instant createdAt,
    List<String> triggers,
    String snapshotJson,
    String body,
    String model,
    String traceId,
    String rootSpanId,
    List<ChatMessage> messages) {}
