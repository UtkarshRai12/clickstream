package com.learning.clickstream.edge.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.config.ApplicationConfig;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks the HMAC and the expiry. A bad token is invalid_token.
 * An expired token is expired_token so the client knows to call /v1/token again.
 * The API key is not consulted here. The signature is the proof until exp.
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class JwtTokenVerifier implements TokenVerifier {
  private final ApplicationConfig config;
  private final Clock clock;

  @Override
  public Caller verify(String compactJwt) {
    if (compactJwt == null || compactJwt.isBlank()) {
      throw new AuthRejection(401, "missing_token");
    }
    try {
      SignedJWT jwt = SignedJWT.parse(compactJwt);
      String secret = HmacSecrets.require(config.getJwtHmacSecret());
      if (!jwt.verify(new MACVerifier(secret.getBytes(StandardCharsets.UTF_8)))) {
        throw new AuthRejection(401, "invalid_token");
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Date exp = claims.getExpirationTime();
      if (exp == null || !exp.toInstant().isAfter(clock.instant())) {
        throw new AuthRejection(401, "expired_token");
      }
      String tenant = claims.getStringClaim("tenant_id");
      String scopeRaw = claims.getStringClaim("scope");
      String keyId = claims.getSubject();
      if (tenant == null || tenant.isBlank() || scopeRaw == null || keyId == null || keyId.isBlank()) {
        throw new AuthRejection(401, "invalid_token");
      }
      return new Caller(keyId, tenant, Scope.valueOf(scopeRaw));
    } catch (AuthRejection rejection) {
      throw rejection;
    } catch (ParseException | IllegalArgumentException ex) {
      log.debug("rejected malformed token", ex);
      throw new AuthRejection(401, "invalid_token");
    } catch (Exception ex) {
      log.debug("rejected unverifiable token", ex);
      throw new AuthRejection(401, "invalid_token");
    }
  }
}
