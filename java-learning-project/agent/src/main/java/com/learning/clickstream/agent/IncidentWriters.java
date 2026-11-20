package com.learning.clickstream.agent;

import com.learning.clickstream.agent.guard.GuardedWriter;
import com.learning.clickstream.agent.guard.LlmTopicGate;
import com.learning.clickstream.agent.guard.TopicClassifier;
import com.learning.clickstream.agent.guard.TopicGuard;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks the writer. A Cloud project means the Gemini platform (Vertex).
 * Otherwise a Gemini key means the AI Studio API. No project and no key means
 * the local writer, and no model call is made at all.
 */
public final class IncidentWriters {
  public static final String DEFAULT_MODEL = "gemini-3.6-flash";
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
   * The writer tests call this. It does not load the embedding model.
   * {@code baseUrl} may be null. Then the client uses Google's Gemini endpoint.
   * The prompt is the hardcoded rules, so a test never calls Langfuse.
   */
  public static IncidentWriter create(
      String apiKey, String modelName, String baseUrl, Duration timeout, int maxRetries) {
    if (apiKey == null) {
      log.info("no Gemini key, reports use the local writer");
      return new LocalIncidentWriter();
    }
    ChatModel model = chatModel(apiKey, modelName, baseUrl, timeout, maxRetries);
    log.info("reports use Gemini model={}", modelName);
    return guarded(model, modelName, PromptSource.hardcoded(LlmIncidentWriter.RULES));
  }

  /**
   * The process entry point. One Gemini client is shared by the writer and the topic gate.
   * A project selects the platform client. That client reads Application Default Credentials
   * and does not use the API key. The key path remains for a machine with no Cloud project.
   */
  public static Running running(
      String apiKey,
      String modelName,
      String baseUrl,
      Duration timeout,
      int maxRetries,
      String gcpProject,
      String gcpLocation,
      Map<String, String> env) {
    if (gcpProject != null && !gcpProject.isBlank()) {
      ChatModel model = platformModel(gcpProject, gcpLocation, modelName, maxRetries);
      log.info("reports use Vertex Gemini model={} project={} location={}", modelName, gcpProject, gcpLocation);
      PromptSource prompts = PromptSource.fromEnv(env, LlmIncidentWriter.RULES);
      return new Running(guarded(model, modelName, prompts), topics(model));
    }
    if (apiKey == null) {
      log.info("no Gemini key, reports use the local writer");
      return new Running(new LocalIncidentWriter(), TopicGuard.allowAll());
    }
    ChatModel model = chatModel(apiKey, modelName, baseUrl, timeout, maxRetries);
    log.info("reports use Gemini model={}", modelName);
    PromptSource prompts = PromptSource.fromEnv(env, LlmIncidentWriter.RULES);
    return new Running(guarded(model, modelName, prompts), topics(model));
  }

  /**
   * Gemini platform client. Calls aiplatform.googleapis.com with the logged-in
   * Google account. Tests do not use this. Their stub speaks the AI Studio HTTP API.
   */
  public static ChatModel platformModel(String project, String location, String modelName, int maxRetries) {
    String region = location == null || location.isBlank() ? "global" : location;
    VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder builder = VertexAiGeminiChatModel.builder()
        .project(project)
        .location(region)
        .modelName(modelName)
        .maxRetries(maxRetries)
        // Low temperature: the note restates numbers, it is not creative writing.
        .temperature(0.2f);
    // "global" is a resource location, not a hostname. The client would otherwise
    // call global-aiplatform.googleapis.com, which does not exist.
    if ("global".equals(region)) {
      builder.apiEndpoint("aiplatform.googleapis.com");
    }
    return builder.build();
  }

  private static IncidentWriter guarded(ChatModel model, String modelName, PromptSource prompts) {
    return new GuardedWriter(new LlmIncidentWriter(model, modelName, prompts));
  }

  /** Embeddings are local. If they fail to load, every question goes to the LLM gate. */
  private static TopicGuard topics(ChatModel model) {
    TopicGuard.Labels labels;
    try {
      labels = new TopicClassifier();
    } catch (RuntimeException ex) {
      log.warn("topic embeddings failed to load, follow-ups use the LLM gate only", ex);
      labels = question -> List.of();
    }
    return new TopicGuard(labels, new LlmTopicGate(model));
  }

  private static ChatModel chatModel(
      String apiKey, String modelName, String baseUrl, Duration timeout, int maxRetries) {
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
    return builder.build();
  }

  /** The writer and the follow-up gate for one process. */
  public record Running(IncidentWriter writer, TopicGuard topics) {}
}
