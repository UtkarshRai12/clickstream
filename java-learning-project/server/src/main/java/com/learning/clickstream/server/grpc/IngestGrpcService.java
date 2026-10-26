package com.learning.clickstream.server.grpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.proto.v1.AcceptAck;
import com.learning.clickstream.proto.v1.IngestEvent;
import com.learning.clickstream.proto.v1.IngestServiceGrpc;
import com.learning.clickstream.server.ingest.BufferedEvent;
import com.learning.clickstream.server.ingest.IngestBuffer;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Implements IngestService.Accept. The ack is sent when the rows are in memory.
 * The flusher writes them to Doris after this RPC completes.
 * The tenant on each event must match the tenant header from the edge.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class IngestGrpcService extends IngestServiceGrpc.IngestServiceImplBase {
  private final IngestBuffer buffer;

  @Override
  public StreamObserver<IngestEvent> accept(StreamObserver<AcceptAck> response) {
    String tenant = ServiceAuthInterceptor.TENANT.get();
    String requestId = ServiceAuthInterceptor.REQUEST_ID.get();
    List<BufferedEvent> rows = new ArrayList<>();
    return new StreamObserver<>() {
      private boolean failed;

      @Override
      public void onNext(IngestEvent event) {
        if (failed) {
          return;
        }
        if (tenant == null || !tenant.equals(event.getTenantId()) || event.getEvent().getEventId().isBlank()) {
          failed = true;
          response.onError(Status.INVALID_ARGUMENT.withDescription("bad event").asRuntimeException());
          return;
        }
        rows.add(new BufferedEvent(
            event.getTenantId(),
            event.getEvent().getEventId(),
            event.getEvent().getUserId(),
            event.getEvent().getEventType(),
            Instant.ofEpochMilli(event.getEvent().getOccurredAtEpochMs()),
            event.getEvent().getPropertiesJson()));
      }

      @Override
      public void onError(Throwable error) {
        // The callback thread does not inherit the edge's MDC. Put the ids here.
        withCallMdc(requestId, tenant, () ->
            log.warn("ingest stream failed requestId={}: {}", requestId, error.toString()));
      }

      @Override
      public void onCompleted() {
        if (failed) {
          return;
        }
        buffer.add(List.copyOf(rows));
        withCallMdc(requestId, tenant, () ->
            log.info("queued events count={} tenant={} requestId={}", rows.size(), tenant, requestId));
        response.onNext(AcceptAck.newBuilder().setAcceptedCount(rows.size()).build());
        response.onCompleted();
      }
    };
  }

  /** Same keys the edge writes, so one request id can be filtered across both files. */
  private static void withCallMdc(String requestId, String tenant, Runnable action) {
    MDC.put("request_id", requestId == null ? "" : requestId);
    MDC.put("tenant_id", tenant == null ? "" : tenant);
    MDC.put("route", "IngestService/Accept");
    try {
      action.run();
    } finally {
      MDC.remove("request_id");
      MDC.remove("tenant_id");
      MDC.remove("route");
    }
  }
}
