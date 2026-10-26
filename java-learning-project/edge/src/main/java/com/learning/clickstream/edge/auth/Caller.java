package com.learning.clickstream.edge.auth;

import lombok.Value;

/**
 * Who the verified token belongs to, for this request only.
 * The filter stores it on the request. Guice exposes it inside the request scope.
 * This is not an HTTP session and it is not written to Doris.
 */
@Value
public class Caller {
  public static final String ATTRIBUTE = Caller.class.getName();

  String keyId;
  String tenantId;
  Scope scope;
}
