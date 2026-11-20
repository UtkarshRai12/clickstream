package com.learning.clickstream.agent;

/** Turns a snapshot into report text, and a follow-up into an answer. */
public interface IncidentWriter {
  /** Stored in the report's model column when this writer produced the text. */
  String modelName();

  WriterResult writeReport(IncidentSnapshot snapshot);

  WriterResult answer(FollowUpQuery query);
}
