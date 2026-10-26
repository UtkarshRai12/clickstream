package com.learning.clickstream.edge.auth;

import java.nio.charset.StandardCharsets;

/**
 * HS256 rejects a key shorter than 256 bits. Nimbus enforces the same minimum.
 * Failing here makes a short dev secret a startup error instead of a 500 on
 * the first token request.
 */
public final class HmacSecrets {
  private HmacSecrets() {}

  public static String require(String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("jwt.hmac-secret must be at least 32 bytes for HS256");
    }
    return secret;
  }
}
