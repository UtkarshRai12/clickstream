package com.learning.clickstream.proto;

/**
 * gRPC metadata names for the hop between the edge and the server.
 * These are HTTP-style headers on the RPC, not protobuf fields.
 * Both processes must use these exact strings.
 */
public final class GrpcHeaderNames {
  public static final String INTERNAL_TOKEN = "x-internal-token";
  public static final String REQUEST_ID = "x-request-id";
  public static final String TENANT_ID = "x-tenant-id";

  private GrpcHeaderNames() {}
}
