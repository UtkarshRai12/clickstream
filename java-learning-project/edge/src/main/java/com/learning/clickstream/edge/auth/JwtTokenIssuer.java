package com.learning.clickstream.edge.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.config.ApplicationConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.RequiredArgsConstructor;

/**
 * Signs a stateless access token. Nothing is stored server-side.
 * Expiry is the revocation mechanism for phase 4. A denylist can wait.
 *
 * Ingest tokens live for jwt.ingest-ttl-seconds (15 minutes in the sample).
 * Report tokens live for jwt.report-ttl-seconds (one hour).
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class JwtTokenIssuer implements TokenIssuer {
  private final ApplicationConfig config;
  private final Clock clock;

  @Override
  public AccessToken issue(ApiKey key, Scope scope) {
    if (!key.getScopes().contains(scope)) {
      throw new AuthRejection(403, "scope_not_allowed");
    }
    int ttl = scope == Scope.INGEST ? config.getIngestTtlSeconds() : config.getReportTtlSeconds();
    Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    Instant exp = now.plusSeconds(ttl);
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .subject(key.getKeyId())
        .claim("tenant_id", key.getTenantId())
        .claim("scope", scope.name())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(exp))
        .build();
    // kid lets a person reading the token see which key minted it. Verification uses sub.
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(key.getKeyId()).build();
    SignedJWT jwt = new SignedJWT(header, claims);
    try {
      String secret = HmacSecrets.require(config.getJwtHmacSecret());
      jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("could not sign token for " + key.getKeyId(), ex);
    }
    return new AccessToken(jwt.serialize(), ttl, scope);
  }
}
