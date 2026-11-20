package com.learning.clickstream.agent.guard;

import java.util.List;

/**
 * Follow-up questions only. A report is never gated: the edge sends a snapshot,
 * not a question. A sure label decides. Anything else, including no label, asks
 * the second gate. A wrong refusal is worse than a chatty answer here.
 */
public final class TopicGuard {
  public static final String REFUSAL =
      "I can only answer questions about this incident report and its snapshot.";

  private final Labels labels;
  private final Gate gate;

  public TopicGuard(Labels labels, Gate gate) {
    this.labels = labels;
    this.gate = gate;
  }

  /** Used when there is no Gemini key, and by tests that are not about topics. */
  public static TopicGuard allowAll() {
    return new TopicGuard(question -> List.of(Topic.ON), question -> true);
  }

  public boolean allows(String question) {
    List<Topic> found = labels.classify(question);
    if (found.equals(List.of(Topic.ON))) {
      return true;
    }
    if (found.equals(List.of(Topic.OFF))) {
      return false;
    }
    return gate.onTopic(question);
  }

  public enum Topic {
    ON,
    OFF
  }

  /** Layer 1. An empty list, or both labels, means "not sure". */
  public interface Labels {
    List<Topic> classify(String question);
  }

  /** Layer 2. Only called when layer 1 is not sure. */
  public interface Gate {
    boolean onTopic(String question);
  }
}
