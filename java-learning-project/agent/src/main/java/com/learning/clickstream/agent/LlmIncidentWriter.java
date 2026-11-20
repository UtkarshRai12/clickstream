package com.learning.clickstream.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
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
      """;

  private static final ObjectMapper JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final ChatModel model;
  private final String modelName;

  public LlmIncidentWriter(ChatModel model, String modelName) {
    this.model = model;
    this.modelName = modelName;
  }

  @Override
  public String modelName() {
    return modelName;
  }

  @Override
  public String writeReport(IncidentSnapshot snapshot) {
    String text = model.chat(
        SystemMessage.from(RULES),
        UserMessage.from("Snapshot JSON:\n" + json(snapshot) + "\n\nWrite the incident report."))
        .aiMessage()
        .text();
    return require(text);
  }

  /**
   * The system message carries the snapshot and the report. The turns that follow
   * are the stored chat, then the new question.
   */
  @Override
  public String answer(FollowUpQuery query) {
    List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
    messages.add(SystemMessage.from(RULES
        + "\nSnapshot JSON:\n" + json(query.snapshot())
        + "\n\nThe report you wrote:\n" + query.reportText()));
    messages.addAll(history(query.messages()));
    messages.add(UserMessage.from(query.question()));
    return require(model.chat(messages).aiMessage().text());
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
