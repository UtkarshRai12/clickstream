package com.learning.clickstream.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the OTLP JSON body Langfuse reads. Trace and span ids are lowercase hex,
 * which is what the OTLP JSON encoding uses. Langfuse maps the {@code langfuse.*}
 * attributes onto the trace name, the session, and the observation type.
 */
final class OtlpJson {
  private static final String SERVICE = "clickstream-agent";

  /** OTLP span kind 1 is INTERNAL. Nothing here is a network call. */
  private static final int INTERNAL = 1;

  private OtlpJson() {}

  static Map<String, Object> body(List<Observation> spans) {
    List<Map<String, Object>> encoded = new ArrayList<>();
    for (Observation span : spans) {
      encoded.add(span(span));
    }
    return Map.of("resourceSpans", List.of(Map.of(
        "resource", Map.of("attributes", List.of(attr("service.name", SERVICE))),
        "scopeSpans", List.of(Map.of(
            "scope", Map.of("name", SERVICE),
            "spans", encoded)))));
  }

  private static Map<String, Object> span(Observation span) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("traceId", span.traceId());
    out.put("spanId", span.spanId());
    // The root has no parent. A follow-up points at the stored root span id.
    if (span.parentSpanId() != null) {
      out.put("parentSpanId", span.parentSpanId());
    }
    out.put("name", span.name());
    out.put("kind", INTERNAL);
    out.put("startTimeUnixNano", nanos(span.start()));
    out.put("endTimeUnixNano", nanos(span.end()));
    out.put("attributes", attributes(span));
    return out;
  }

  private static List<Map<String, Object>> attributes(Observation span) {
    List<Map<String, Object>> attrs = new ArrayList<>();
    // Copied onto every span, so a follow-up span alone still lands in the right trace and session.
    attrs.add(attr("langfuse.trace.name", span.traceName()));
    attrs.add(attr("langfuse.session.id", span.sessionId()));
    attrs.add(attr("session.id", span.sessionId()));
    attrs.add(attr("langfuse.observation.type", span.type()));
    attrs.add(attr("langfuse.observation.input", span.input()));
    attrs.add(attr("langfuse.observation.output", span.output()));
    if (Observation.GENERATION.equals(span.type()) && span.model() != null) {
      attrs.add(attr("langfuse.observation.model.name", span.model()));
    }
    // One attribute per key. Langfuse reads the suffix as observation metadata.
    for (Map.Entry<String, String> entry : span.metadata().entrySet()) {
      attrs.add(attr("langfuse.observation.metadata." + entry.getKey(), entry.getValue()));
    }
    if (Observation.GENERATION.equals(span.type()) && span.inputTokens() != null) {
      int output = span.outputTokens() == null ? 0 : span.outputTokens();
      attrs.add(attr(
          "langfuse.observation.usage_details",
          "{\"input\":" + span.inputTokens() + ",\"output\":" + output + "}"));
    }
    if (span.promptName() != null) {
      attrs.add(attr("langfuse.observation.prompt.name", span.promptName()));
      if (span.promptVersion() != null) {
        attrs.add(attrInt("langfuse.observation.prompt.version", span.promptVersion()));
      }
    }
    return attrs;
  }

  private static Map<String, Object> attr(String key, String value) {
    return Map.of("key", key, "value", Map.of("stringValue", value == null ? "" : value));
  }

  /** Prompt versions are integers. A string would not link the generation to the prompt. */
  private static Map<String, Object> attrInt(String key, int value) {
    return Map.of("key", key, "value", Map.of("intValue", value));
  }

  /** A string, because a 64-bit nano count does not fit a JSON number safely. */
  private static String nanos(Instant at) {
    return Long.toString(at.getEpochSecond() * 1_000_000_000L + at.getNano());
  }
}
