package com.learning.clickstream.edge.http;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.auth.Caller;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;

/**
 * GET /v1/reports/daily is protected by JwtAuthFilter before this runs.
 * Phase 6 fills the rows from Doris through gRPC. Phase 4 only proves that a
 * REPORT token reaches this servlet and that the tenant comes from the token.
 * The response echoes tenant_id so a test can see the request-scoped Caller.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ReportServlet extends HttpServlet {
  private final Provider<Caller> caller;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Caller current = caller.get();
    HttpResponses.json(response, 200, "{"
        + "\"tenant_id\":" + HttpResponses.quote(current.getTenantId())
        + ",\"rows\":[]}");
  }
}
