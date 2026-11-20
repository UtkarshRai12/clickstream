package com.learning.clickstream.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.learning.clickstream.agent.guard.TopicGuard;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the report and the follow-up answers with a chat model (Gemini in this
 * project). The model sees the stored snapshot JSON every time, so a later turn
 * cannot drift off the numbers. Any failure here is caught by IncidentService,
 * which stores the local text instead.
 */
public final class LlmIncidentWriter implements IncidentWriter {
  /** The rule every call starts with. The snapshot is the only evidence. */
  static final String RULES = """
      You write short incident notes about the ingress hop between the clickstream edge and the ingest server.
      Use only the numbers in the snapshot JSON. Do not invent a cause, a tenant, or anything about Doris.
      The edge has already returned 202 for the batches it accepted.
      Say what crossed, quote the counts, and say what to check next.
      Plain text, no markdown headings, under 200 words.
      If the question is not about this report, reply with exactly this sentence: %s
      """.formatted(TopicGuard.REFUSAL);

  private static final ObjectMapper JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final ChatModel model;
  private final String modelName;
  private final PromptSource prompts;

  public LlmIncidentWriter(ChatModel model, String modelName, PromptSource prompts) {
    this.model = model;
    this.modelName = modelName;
    this.prompts = prompts;
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public WriterResult writeReport(IncidentSnapshot snapshot) {
    PromptSource.PromptText rules = prompts.get();
    return result(rules, model.chat(
        SystemMessage.from(rules.text()),
        UserMessage.from("Snapshot JSON:\n" + json(snapshot) + "\n\nWrite the incident report.")));
  }

  /**
   * The system message carries the snapshot and the report. The turns that follow
   * are the stored chat, then the new question.
   */
  @Override
  public WriterResult answer(FollowUpQuery query) {
    PromptSource.PromptText rules = prompts.get();
    List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
    messages.add(SystemMessage.from(rules.text()
        + "\nSnapshot JSON:\n" + json(query.snapshot())
        + "\n\nThe report you wrote:\n" + query.reportText()));
    messages.addAll(history(query.messages()));
    messages.add(UserMessage.from(query.question()));
    return result(rules, model.chat(messages));
  }

  /** Empty text is a failure. Token counts ride along so the span can be priced. */
  private WriterResult result(PromptSource.PromptText rules, ChatResponse response) {
    TokenUsage usage = response.tokenUsage();
    return WriterResult.model(
        require(response.aiMessage().text()),
        modelName,
        usage == null ? null : usage.inputTokenCount(),
        usage == null ? null : usage.outputTokenCount(),
        rules.name(),
        rules.version());
  }

  /**
   * Stored turns as chat messages. Leading assistant turns are skipped. The first
   * one is the report, which is already in the system message, and Gemini expects
   * the conversation to start with a user turn.
   */
  private static List<dev.langchain4j.data.message.ChatMessage> history(List<ChatMessage> stored) {
    List<dev.langchain4j.data.message.ChatMessage> out = new ArrayList<>();
    for (ChatMessage turn : stored) {
      boolean user = "user".equals(turn.role());
      if (out.isEmpty() && !user) {
        continue;
      }
      out.add(user ? UserMessage.from(turn.content()) : AiMessage.from(turn.content()));
    }
    return out;
  }

  /** An empty answer is a failure, so the service falls back to the local text. */
  private static String require(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalStateException("model returned no text");
    }
    return text;
  }

  private static String json(IncidentSnapshot snapshot) {
    try {
      return JSON.writeValueAsString(snapshot);
    } catch (JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
