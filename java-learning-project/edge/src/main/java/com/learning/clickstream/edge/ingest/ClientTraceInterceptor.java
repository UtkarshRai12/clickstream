package com.learning.clickstream.edge.ingest;

import com.learning.clickstream.proto.GrpcHeaderNames;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Attaches the internal token, the request id, and the tenant to one RPC.
 * The public API key and the JWT stay on the edge. The server checks the token.
 */
public final class ClientTraceInterceptor implements ClientInterceptor {
  private static final Metadata.Key<String> INTERNAL =
      Metadata.Key.of(GrpcHeaderNames.INTERNAL_TOKEN, Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> REQUEST_ID =
      Metadata.Key.of(GrpcHeaderNames.REQUEST_ID, Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> TENANT =
      Metadata.Key.of(GrpcHeaderNames.TENANT_ID, Metadata.ASCII_STRING_MARSHALLER);

  private final String internalToken;
  private final String requestId;
  private final String tenantId;

  public ClientTraceInterceptor(String internalToken, String requestId, String tenantId) {
    this.internalToken = internalToken;
    this.requestId = requestId;
    this.tenantId = tenantId;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        headers.put(INTERNAL, internalToken);
        headers.put(REQUEST_ID, requestId);
        headers.put(TENANT, tenantId);
        super.start(responseListener, headers);
      }
    };
  }
}
