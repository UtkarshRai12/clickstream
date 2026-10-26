package com.learning.clickstream.edge.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.http.HttpResponses;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Runs before the ingest and report servlets. The API key is not accepted here.
 * Clients exchange that key at POST /v1/token, then send Authorization: Bearer.
 * A token whose scope does not match the path is 403. The servlet does not run.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class JwtAuthFilter implements Filter {
  private static final String BEARER = "Bearer ";

  private final TokenVerifier tokens;
  private final RouteScopes routes;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest http = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    try {
      String header = http.getHeader("Authorization");
      if (header == null || !header.startsWith(BEARER)) {
        throw new AuthRejection(401, "missing_token");
      }
      Caller caller = tokens.verify(header.substring(BEARER.length()).trim());
      Scope required = routes.requiredFor(http.getRequestURI());
      if (caller.getScope() != required) {
        throw new AuthRejection(403, "wrong_scope");
      }
      http.setAttribute(Caller.ATTRIBUTE, caller);
      // The outer RequestIdFilter removes this when the request ends.
      MDC.put("tenant_id", caller.getTenantId());
      chain.doFilter(request, response);
    } catch (AuthRejection rejection) {
      log.warn("rejected auth code={} path={}", rejection.getCode(), http.getRequestURI());
      HttpResponses.error(resp, rejection.getStatus(), rejection.getCode());
    }
  }
}
