package com.learning.clickstream.agent;

import java.time.Instant;

/**
 * One span in an ingress trace. {@link LangfuseExporter} turns these fields
 * into OTLP JSON. {@code start} and {@code end} bracket the writer call, so
 * Langfuse can show how long the report or the answer took.
 */
public record Observation(
    String traceName,
    String traceId,
    String spanId,
    String parentSpanId,
    String sessionId,
    String name,
    String type,
    String model,
    String input,
    String output,
    Instant start,
    Instant end) {

  static final String TRACE = "ingress-incident";
  static final String WRITE_REPORT = "write-report";
  static final String FOLLOW_UP = "follow-up";
  static final String AGENT = "agent";
  static final String GENERATION = "generation";

  /** The root span. Its parent is empty because nothing in this trace wraps it. */
  static Observation agent(
      String traceId,
      String spanId,
      String sessionId,
      String model,
      String input,
      String output,
      Instant start,
      Instant end) {
    return new Observation(
        TRACE, traceId, spanId, null, sessionId, TRACE, AGENT, model, input, output, start, end);
  }

  /** A child generation. A follow-up uses the stored root span id as {@code parentSpanId}. */
  static Observation generation(
      String traceId,
      String spanId,
      String parentSpanId,
      String sessionId,
      String name,
      String model,
      String input,
      String output,
      Instant start,
      Instant end) {
    return new Observation(
        TRACE, traceId, spanId, parentSpanId, sessionId, name, GENERATION, model, input, output,
        start, end);
  }
}
