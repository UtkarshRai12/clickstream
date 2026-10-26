package com.learning.clickstream.server.grpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.proto.GrpcHeaderNames;
import com.learning.clickstream.server.ServerConfig;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;

/**
 * The server does not parse the public JWT. It requires the shared internal token
 * the edge puts on every call. A missing or wrong token closes the RPC and the
 * service method does not run.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ServiceAuthInterceptor implements ServerInterceptor {
  public static final Context.Key<String> TENANT = Context.key("tenant");
  public static final Context.Key<String> REQUEST_ID = Context.key("request-id");

  private static final Metadata.Key<String> INTERNAL =
      Metadata.Key.of(GrpcHeaderNames.INTERNAL_TOKEN, Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> REQUEST =
      Metadata.Key.of(GrpcHeaderNames.REQUEST_ID, Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> TENANT_HEADER =
      Metadata.Key.of(GrpcHeaderNames.TENANT_ID, Metadata.ASCII_STRING_MARSHALLER);

  private final ServerConfig config;

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String presented = headers.get(INTERNAL);
    if (!matches(presented, config.getInternalToken())) {
      call.close(Status.UNAUTHENTICATED.withDescription("bad internal token"), new Metadata());
      return new ServerCall.Listener<>() {};
    }
    String tenant = headers.get(TENANT_HEADER);
    String requestId = headers.get(REQUEST);
    if (tenant == null || tenant.isBlank()) {
      call.close(Status.INVALID_ARGUMENT.withDescription("missing tenant"), new Metadata());
      return new ServerCall.Listener<>() {};
    }
    Context context = Context.current()
        .withValue(TENANT, tenant)
        .withValue(REQUEST_ID, requestId == null ? "" : requestId);
    return Contexts.interceptCall(context, call, headers, next);
  }

  private static boolean matches(String presented, String expected) {
    if (presented == null) {
      return false;
    }
    return MessageDigest.isEqual(
        presented.getBytes(StandardCharsets.UTF_8),
        expected.getBytes(StandardCharsets.UTF_8));
  }
}
