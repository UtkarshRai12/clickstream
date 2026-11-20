package com.learning.clickstream.agent;

import java.time.Instant;
import java.util.Map;

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
    Instant end,
    Map<String, String> metadata,
    Integer inputTokens,
    Integer outputTokens,
    String promptName,
    Integer promptVersion) {

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
      Instant end,
      String guardrail) {
    return new Observation(
        TRACE, traceId, spanId, null, sessionId, TRACE, AGENT, model, input, output, start, end,
        guard(guardrail), null, null, null, null);
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
      Instant end,
      Integer inputTokens,
      Integer outputTokens,
      String promptName,
      Integer promptVersion,
      String guardrail) {
    return new Observation(
        TRACE, traceId, spanId, parentSpanId, sessionId, name, GENERATION, model, input, output,
        start, end, guard(guardrail), inputTokens, outputTokens, promptName, promptVersion);
  }

  /**
   * A follow-up that never called the model. Type {@code span}, so Langfuse does
   * not price it as a generation.
   */
  static Observation refused(
      String traceId,
      String spanId,
      String parentSpanId,
      String sessionId,
      String input,
      String output,
      Instant start,
      Instant end) {
    return new Observation(
        TRACE, traceId, spanId, parentSpanId, sessionId, FOLLOW_UP, "span", null, input, output,
        start, end, guard("off_topic"), null, null, null, null);
  }

  private static Map<String, String> guard(String guardrail) {
    return guardrail == null ? Map.of() : Map.of("guardrail", guardrail);
  }
}
