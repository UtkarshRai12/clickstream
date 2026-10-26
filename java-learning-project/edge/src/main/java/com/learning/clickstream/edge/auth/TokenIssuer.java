package com.learning.clickstream.edge.auth;

/** Mints one access token for one scope the key is allowed to hold. */
public interface TokenIssuer {
  AccessToken issue(ApiKey key, Scope scope);
}
