package com.learning.clickstream.agent;

/**
 * One span in an ingress trace. The real HTTP export comes later. This phase
 * only records these fields so the service already has the trace shape.
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
    String output) {

  static final String TRACE = "ingress-incident";
  static final String WRITE_REPORT = "write-report";
  static final String FOLLOW_UP = "follow-up";
  static final String AGENT = "agent";
  static final String GENERATION = "generation";

  /** The root span. Its parent is empty because nothing in this trace wraps it. */
  static Observation agent(
      String traceId, String spanId, String sessionId, String model, String input, String output) {
    return new Observation(TRACE, traceId, spanId, null, sessionId, TRACE, AGENT, model, input, output);
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
      String output) {
    return new Observation(
        TRACE, traceId, spanId, parentSpanId, sessionId, name, GENERATION, model, input, output);
  }
}
