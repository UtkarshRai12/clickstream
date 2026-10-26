package com.learning.clickstream.edge.auth;

/**
 * Which scope a path requires. The filter is only mounted on these paths.
 * /v1/token is not here. That route accepts the API key and mints the token.
 */
public final class RouteScopes {
  public Scope requiredFor(String path) {
    if (path.startsWith("/v1/reports/")) {
      return Scope.REPORT;
    }
    if ("/v1/events".equals(path) || "/v1/events:batch".equals(path)) {
      return Scope.INGEST;
    }
    throw new IllegalArgumentException("no scope mapped for " + path);
  }
}
