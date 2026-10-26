package com.learning.clickstream.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.learning.clickstream.proto.v1.Event;
import com.learning.clickstream.proto.v1.IngestServiceGrpc;
import com.learning.clickstream.proto.v1.QueryServiceGrpc;
import org.junit.jupiter.api.Test;

/** Phase 3 exit check: protoc and the gRPC plugin both ran, and a message survives a round trip. */
class ProtoCompilesTest {

  @Test
  void eventRoundTripsThroughProtobufBytes() throws Exception {
    Event event = Event.newBuilder()
        .setEventId("e1")
        .setUserId("u1")
        .setEventType("page_view")
        .setOccurredAtEpochMs(1_700_000_000_000L)
        .setPropertiesJson("{}")
        .build();

    Event parsed = Event.parseFrom(event.toByteArray());

    assertEquals("e1", parsed.getEventId());
    assertEquals("page_view", parsed.getEventType());
  }

  @Test
  void grpcStubsExposeTheTwoRpcs() {
    // Touching the descriptors fails compilation if compile-custom did not run.
    assertNotNull(IngestServiceGrpc.getAcceptMethod());
    assertNotNull(QueryServiceGrpc.getDailyReportMethod());
    assertEquals("clickstream.v1.IngestService", IngestServiceGrpc.SERVICE_NAME);
    assertEquals("clickstream.v1.QueryService", QueryServiceGrpc.SERVICE_NAME);
  }
}
