package com.learning.clickstream.edge.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RouteScopesTest {

  @Test
  void pathsMapToTheScopeTheFilterEnforces() {
    RouteScopes routes = new RouteScopes();
    assertEquals(Scope.INGEST, routes.requiredFor("/v1/events"));
    assertEquals(Scope.INGEST, routes.requiredFor("/v1/events:batch"));
    assertEquals(Scope.REPORT, routes.requiredFor("/v1/reports/daily"));
    assertThrows(IllegalArgumentException.class, () -> routes.requiredFor("/v1/token"));
  }
}
