package com.learning.clickstream.edge.ingest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.config.ApplicationConfig;
import com.learning.clickstream.proto.v1.AcceptAck;
import com.learning.clickstream.proto.v1.Event;
import com.learning.clickstream.proto.v1.IngestEvent;
import com.learning.clickstream.proto.v1.IngestServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;

/**
 * Client-streaming Accept RPC. The dispatcher thread blocks here until the
 * server acks or the deadline hits. The HTTP thread does not call this.
 * The ack means the server queued the rows, not that Doris has them.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class GrpcIngestClient implements IngestClient {
  private final ManagedChannel channel;
  private final ApplicationConfig config;

  @Override
  public void accept(QueuedBatch batch) {
    IngestServiceGrpc.IngestServiceStub stub = IngestServiceGrpc.newStub(channel)
        .withDeadlineAfter(5, TimeUnit.SECONDS)
        .withInterceptors(new ClientTraceInterceptor(
            config.getInternalToken(), batch.getRequestId(), batch.getTenantId()));
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    StreamObserver<IngestEvent> outbound = stub.accept(new StreamObserver<>() {
      @Override
      public void onNext(AcceptAck ack) {
        // The count is the server's view. A mismatch is logged by the dispatcher via failure.
      }

      @Override
      public void onError(Throwable error) {
        failure.set(error);
        done.countDown();
      }

      @Override
      public void onCompleted() {
        done.countDown();
      }
    });
    for (IncomingEvent event : batch.getEvents()) {
      outbound.onNext(IngestEvent.newBuilder()
          .setTenantId(batch.getTenantId())
          .setEvent(Event.newBuilder()
              .setEventId(event.getEventId())
              .setUserId(event.getUserId())
              .setEventType(event.getEventType())
              .setOccurredAtEpochMs(event.getOccurredAt().toEpochMilli())
              .setPropertiesJson(event.getPropertiesJson())
              .build())
          .build());
    }
    outbound.onCompleted();
    try {
      if (!done.await(6, TimeUnit.SECONDS)) {
        cancel(outbound);
        throw Status.DEADLINE_EXCEEDED.withDescription("ingest ack timed out").asRuntimeException();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      cancel(outbound);
      throw Status.CANCELLED.withCause(ex).asRuntimeException();
    }
    if (failure.get() != null) {
      throw Status.fromThrowable(failure.get()).asRuntimeException();
    }
  }

  private static void cancel(StreamObserver<IngestEvent> outbound) {
    if (outbound instanceof ClientCallStreamObserver<?> call) {
      call.cancel("deadline", null);
    }
  }
}
