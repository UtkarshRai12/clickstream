package com.learning.clickstream.agent;

import java.time.Instant;

/** One turn on a stored report. The first turn is the report body, role assistant. */
public record ChatMessage(long id, String role, String content, Instant createdAt) {}
