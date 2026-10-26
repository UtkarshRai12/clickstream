package com.learning.clickstream.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.learning.clickstream.proto.v1.IngestServiceGrpc;
import com.learning.clickstream.proto.v1.QueryServiceGrpc;
import org.junit.jupiter.api.Test;

/** Phase 3: the server module compiles against the generated stubs. */
class ProtoStubClasspathTest {

  @Test
  void serverSeesBothServices() {
    assertNotNull(IngestServiceGrpc.getAcceptMethod());
    assertNotNull(QueryServiceGrpc.getDailyReportMethod());
  }
}
