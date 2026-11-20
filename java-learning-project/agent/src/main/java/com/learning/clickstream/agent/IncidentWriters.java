package com.learning.clickstream.agent;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks the writer. A Gemini key in the environment means Gemini writes the text.
 * No key means the local writer, and no model call is made at all.
 */
public final class IncidentWriters {
  public static final String DEFAULT_MODEL = "gemini-2.5-flash";
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private static final Logger log = LoggerFactory.getLogger(IncidentWriters.class);

  private IncidentWriters() {}

  /** GEMINI_API_KEY first, then GEMINI_AI_KEY. The key is never put in a properties file. */
  public static String apiKey(Map<String, String> env) {
    String key = env.get("GEMINI_API_KEY");
    if (key == null || key.isBlank()) {
      key = env.get("GEMINI_AI_KEY");
    }
    return key == null || key.isBlank() ? null : key;
  }

  /**
   * {@code baseUrl} may be null. Then the client uses Google's Gemini endpoint.
   * Tests pass a local stub URL so no request leaves the machine.
   */
  public static IncidentWriter create(
      String apiKey, String modelName, String baseUrl, Duration timeout, int maxRetries) {
    if (apiKey == null) {
      log.info("no Gemini key, reports use the local writer");
      return new LocalIncidentWriter();
    }
    GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        .timeout(timeout)
        .maxRetries(maxRetries)
        // Low temperature: the note restates numbers, it is not creative writing.
        .temperature(0.2);
    if (baseUrl != null && !baseUrl.isBlank()) {
      builder.baseUrl(baseUrl);
    }
    log.info("reports use Gemini model={}", modelName);
    return new LlmIncidentWriter(builder.build(), modelName);
  }
}
