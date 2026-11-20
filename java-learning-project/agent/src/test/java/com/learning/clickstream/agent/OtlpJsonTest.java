package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Usage and the prompt version are attributes. A local span has neither. */
class OtlpJsonTest {
  private static final Instant AT = Instant.parse("2026-11-06T15:50:00Z");
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void aGenerationCarriesUsageAndThePromptVersion() throws Exception {
    Observation generation = Observation.generation(
        "a".repeat(32),
        "b".repeat(16),
        "c".repeat(16),
        "session",
        Observation.WRITE_REPORT,
        "gemini-2.5-flash",
        "in",
        "out",
        AT,
        AT,
        123,
        45,
        PromptSource.NAME,
        3,
        null);

    JsonNode span = spans(generation).get(0);

    assertEquals("{\"input\":123,\"output\":45}", attr(span, "langfuse.observation.usage_details"));
    assertEquals(PromptSource.NAME, attr(span, "langfuse.observation.prompt.name"));
    assertEquals(3, intAttr(span, "langfuse.observation.prompt.version"));
  }

  @Test
  void aLocalSpanOmitsUsage() throws Exception {
    Observation generation = Observation.generation(
        "a".repeat(32),
        "b".repeat(16),
        "c".repeat(16),
        "session",
        Observation.WRITE_REPORT,
        "local",
        "in",
        "out",
        AT,
        AT,
        null,
        null,
        null,
        null,
        "none");

    JsonNode span = spans(generation).get(0);

    assertNull(attr(span, "langfuse.observation.usage_details"));
    assertNull(attr(span, "langfuse.observation.prompt.name"));
    assertEquals("none", attr(span, "langfuse.observation.metadata.guardrail"));
  }

  private JsonNode spans(Observation observation) throws Exception {
    String body = json.writeValueAsString(OtlpJson.body(List.of(observation)));
    return json.readTree(body).path("resourceSpans").get(0).path("scopeSpans").get(0).path("spans");
  }

  private static String attr(JsonNode span, String key) {
    JsonNode value = attribute(span, key);
    return value == null ? null : value.path("stringValue").asText();
  }

  private static int intAttr(JsonNode span, String key) {
    return attribute(span, key).path("intValue").asInt();
  }

  private static JsonNode attribute(JsonNode span, String key) {
    for (JsonNode attribute : span.path("attributes")) {
      if (key.equals(attribute.path("key").asText())) {
        return attribute.path("value");
      }
    }
    return null;
  }
}
