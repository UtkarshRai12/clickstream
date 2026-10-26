package com.learning.clickstream.edge.ingest;

import java.util.List;
import lombok.Value;

/** A batch the HTTP thread already accepted. The dispatcher sends this over gRPC. */
@Value
public class QueuedBatch {
  String tenantId;
  String requestId;
  List<IncomingEvent> events;
}
