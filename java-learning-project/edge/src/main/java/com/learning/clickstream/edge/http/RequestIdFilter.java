package com.learning.clickstream.edge.http;

import com.google.inject.Singleton;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Assigns a request id for every HTTP call. The ingest path copies it onto the
 * queued batch so the gRPC hop and the server log can name the same call.
 * The header from the client is kept when it is short and has no spaces.
 */
@Singleton
public final class RequestIdFilter implements Filter {
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
    HttpServletRequest http = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    String id = http.getHeader("X-Request-Id");
    if (id == null || id.isBlank() || id.length() > 64 || id.indexOf(' ') >= 0) {
      id = UUID.randomUUID().toString();
    }
    http.setAttribute(RequestIds.ATTRIBUTE, id);
    resp.setHeader("X-Request-Id", id);
    // route is the HTTP path. tenant_id is added later, once a key or JWT is known.
    MDC.put("request_id", id);
    MDC.put("route", http.getRequestURI());
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
    } finally {
      // Clear every key this request may have set, including tenant_id from an inner filter.
      MDC.remove("request_id");
      MDC.remove("tenant_id");
      MDC.remove("route");
    }
  }
}
