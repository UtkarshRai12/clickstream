package com.learning.clickstream.edge.http;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.metrics.EdgeMetrics;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;

/**
 * Counts the HTTP result after the servlet has set the status.
 * Bound first so it wraps every other filter. A 202 is not counted here.
 * IngestAcceptance adds the event count for that case, because one call can
 * accept many events. Report calls are left alone. Phase 6 owns those.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class MetricsFilter implements Filter {
  private final EdgeMetrics metrics;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
    HttpServletRequest http = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    try {
      chain.doFilter(request, response);
    } catch (Exception ex) {
      if (ex instanceof IOException io) {
        throw io;
      }
      if (ex instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException(ex);
    }
    int status = resp.getStatus();
    String path = http.getRequestURI();
    if ("/v1/token".equals(path) && "POST".equals(http.getMethod())) {
      if (status == 200) {
        metrics.tokenIssued();
      } else if (status >= 400) {
        metrics.tokenRejected(status);
      }
      return;
    }
    // 401 and 403 on the ingest paths are rejected events. A report 403 is not.
    if (isIngest(path) && status >= 400) {
      metrics.rejected(status);
    }
  }

  private static boolean isIngest(String path) {
    return "/v1/events".equals(path) || "/v1/events:batch".equals(path);
  }
}
