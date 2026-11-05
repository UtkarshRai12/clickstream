package com.learning.clickstream.agent;

import java.util.List;

/**
 * Called after the report row is committed. A failure here must not remove that
 * row. The test double records the batch and does not open a socket.
 */
public interface TraceExporter {
  void export(List<Observation> spans);
}
