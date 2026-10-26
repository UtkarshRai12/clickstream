package com.learning.clickstream.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.learning.clickstream.proto.v1.IngestServiceGrpc;
import com.learning.clickstream.proto.v1.QueryServiceGrpc;
import org.junit.jupiter.api.Test;

/** Phase 3: the edge module compiles against the generated stubs. */
class ProtoStubClasspathTest {

  @Test
  void edgeSeesBothServices() {
    assertEquals("clickstream.v1.IngestService", IngestServiceGrpc.SERVICE_NAME);
    assertEquals("clickstream.v1.QueryService", QueryServiceGrpc.SERVICE_NAME);
  }
}
