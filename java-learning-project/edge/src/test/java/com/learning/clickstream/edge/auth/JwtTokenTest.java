package com.learning.clickstream.edge.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.learning.clickstream.edge.config.ApplicationConfig;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtTokenTest {
  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

  @Test
  void signedTokenVerifiesUntilExpiry() {
    ApplicationConfig config = config(60, 120);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    ApiKey key = new ApiKey("demo-ingest", "tenant-a", "hash", Set.of(Scope.INGEST));

    AccessToken access = new JwtTokenIssuer(config, clock).issue(key, Scope.INGEST);
    Caller caller = new JwtTokenVerifier(config, clock).verify(access.getToken());

    assertEquals(Scope.INGEST, caller.getScope());
    assertEquals("tenant-a", caller.getTenantId());
    assertEquals("demo-ingest", caller.getKeyId());
    assertEquals(60, access.getExpiresInSeconds());
  }

  @Test
  void expiredTokenIsRejected() {
    ApplicationConfig config = config(60, 120);
    ApiKey key = new ApiKey("demo-ingest", "tenant-a", "hash", Set.of(Scope.INGEST));
    AccessToken access = new JwtTokenIssuer(config, Clock.fixed(NOW, ZoneOffset.UTC)).issue(key, Scope.INGEST);

    JwtTokenVerifier later = new JwtTokenVerifier(config, Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));
    AuthRejection rejection = assertThrows(AuthRejection.class, () -> later.verify(access.getToken()));

    assertEquals(401, rejection.getStatus());
    assertEquals("expired_token", rejection.getCode());
  }

  @Test
  void keyCannotMintAScopeItDoesNotHold() {
    ApplicationConfig config = config(60, 120);
    ApiKey key = new ApiKey("demo-ingest", "tenant-a", "hash", Set.of(Scope.INGEST));

    AuthRejection rejection = assertThrows(AuthRejection.class,
        () -> new JwtTokenIssuer(config, Clock.fixed(NOW, ZoneOffset.UTC)).issue(key, Scope.REPORT));

    assertEquals(403, rejection.getStatus());
    assertEquals("scope_not_allowed", rejection.getCode());
  }

  @Test
  void tamperedTokenIsRejected() {
    ApplicationConfig config = config(60, 120);
    ApiKey key = new ApiKey("demo-ingest", "tenant-a", "hash", Set.of(Scope.INGEST));
    String token = new JwtTokenIssuer(config, Clock.fixed(NOW, ZoneOffset.UTC)).issue(key, Scope.INGEST).getToken();
    String broken = token.substring(0, token.length() - 2) + "aa";

    AuthRejection rejection = assertThrows(AuthRejection.class,
        () -> new JwtTokenVerifier(config, Clock.fixed(NOW, ZoneOffset.UTC)).verify(broken));

    assertEquals("invalid_token", rejection.getCode());
  }

  private static ApplicationConfig config(int ingestTtl, int reportTtl) {
    return ApplicationConfig.builder()
        .httpPort(0)
        .jwtHmacSecret("test-hmac-secret-must-be-32-bytes")
        .ingestTtlSeconds(ingestTtl)
        .reportTtlSeconds(reportTtl)
        .apiKeyFile(Path.of("unused"))
        .build();
  }
}
