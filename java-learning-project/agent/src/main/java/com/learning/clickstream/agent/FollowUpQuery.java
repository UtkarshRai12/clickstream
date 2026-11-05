package com.learning.clickstream.agent;

import java.util.List;

/**
 * What a follow-up is allowed to see. The snapshot is the one stored on the
 * report, not a fresh read of the edge. {@code messages} is the last 20 turns.
 */
public record FollowUpQuery(
    IncidentSnapshot snapshot,
    String reportText,
    List<ChatMessage> messages,
    String question) {

  public FollowUpQuery {
    messages = List.copyOf(messages);
  }
}
