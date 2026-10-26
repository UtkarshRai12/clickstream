package com.learning.clickstream.edge.http;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.auth.AccessToken;
import com.learning.clickstream.edge.auth.ApiKey;
import com.learning.clickstream.edge.auth.ApiKeyVerifier;
import com.learning.clickstream.edge.auth.AuthRejection;
import com.learning.clickstream.edge.auth.Scope;
import com.learning.clickstream.edge.auth.TokenIssuer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * POST /v1/token exchanges an API key for one JWT.
 * There is no bearer token on this route. The filter is not mounted here.
 * If the key has a single scope, that scope is used. A key with several scopes
 * must pass ?scope=INGEST or ?scope=REPORT so the token stays narrow.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class TokenServlet extends HttpServlet {
  private final ApiKeyVerifier keys;
  private final TokenIssuer tokens;

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      ApiKey key = keys.verify(request.getHeader("X-Api-Key"));
      // This route has no JWT yet. The tenant comes from the key that just matched.
      MDC.put("tenant_id", key.getTenantId());
      Scope scope = resolveScope(key, request.getParameter("scope"));
      AccessToken access = tokens.issue(key, scope);
      // The token value is not logged. key id and scope are enough to audit issuance.
      log.info("issued token keyId={} scope={} expiresInSeconds={}",
          key.getKeyId(), access.getScope(), access.getExpiresInSeconds());
      HttpResponses.json(response, 200, "{"
          + "\"token\":" + HttpResponses.quote(access.getToken())
          + ",\"expires_in_seconds\":" + access.getExpiresInSeconds()
          + ",\"scope\":" + HttpResponses.quote(access.getScope().name())
          + "}");
    } catch (AuthRejection rejection) {
      log.warn("token request rejected code={}", rejection.getCode());
      HttpResponses.error(response, rejection.getStatus(), rejection.getCode());
    }
  }

  private static Scope resolveScope(ApiKey key, String requested) {
    if (requested == null || requested.isBlank()) {
      if (key.getScopes().size() != 1) {
        throw new AuthRejection(400, "scope_required");
      }
      return key.getScopes().iterator().next();
    }
    return Scope.fromRequest(requested);
  }
}
