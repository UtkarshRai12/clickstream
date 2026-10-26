package com.learning.clickstream.edge.ingest;

import lombok.Getter;

/**
 * The HTTP ingest path rejected the body before queueing it.
 * index is the position inside a batch. A single event uses 0.
 * reason is a short field name or rule, not the caller's payload.
 */
@Getter
public final class IngestRejection extends RuntimeException {
  private final int status;
  private final String code;
  private final Integer index;
  private final String reason;

  public IngestRejection(int status, String code, Integer index, String reason) {
    super(code);
    this.status = status;
    this.code = code;
    this.index = index;
    this.reason = reason;
  }
}
