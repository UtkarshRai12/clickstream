package com.learning.clickstream.agent;

/** Turns a snapshot into report text, and a follow-up into an answer. */
public interface IncidentWriter {
  /** Stored in the report's model column. {@code local} means no model was called. */
  String modelName();

  String writeReport(IncidentSnapshot snapshot);

  String answer(FollowUpQuery query);
}
