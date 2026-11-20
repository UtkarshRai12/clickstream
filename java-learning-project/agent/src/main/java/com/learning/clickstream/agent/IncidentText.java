package com.learning.clickstream.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Snapshot JSON and the short strings the service stores. Kept out of the service so that file stays small. */
final class IncidentText {
  static final int BODY_CAP = 16_000;
  static final int HISTORY = 20;

  private static final ObjectMapper JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private IncidentText() {}

  static String cap(String body) {
    return body.length() <= BODY_CAP ? body : body.substring(0, BODY_CAP);
  }

  static List<ChatMessage> last(List<ChatMessage> messages) {
    if (messages.size() <= HISTORY) {
      return messages;
    }
    return List.copyOf(messages.subList(messages.size() - HISTORY, messages.size()));
  }

  /** The observation input. This is not the Langfuse prompt. That one is the rules text. */
  static String prompt(String snapshotJson, String question) {
    String text = "Use only these numbers. Do not invent a cause.\n" + snapshotJson;
    if (question == null) {
      return text;
    }
    return text + "\nQuestion: " + question;
  }

  static String toJson(IncidentSnapshot snapshot) {
    try {
      return JSON.writeValueAsString(snapshot);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  static IncidentSnapshot fromJson(String raw) {
    try {
      return JSON.readValue(raw, IncidentSnapshot.class);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
