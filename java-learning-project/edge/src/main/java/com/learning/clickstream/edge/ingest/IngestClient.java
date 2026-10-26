package com.learning.clickstream.edge.ingest;

/** Sends one already-accepted batch to the ingest server. */
public interface IngestClient {
  void accept(QueuedBatch batch);
}
