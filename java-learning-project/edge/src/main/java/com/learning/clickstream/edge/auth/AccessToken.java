package com.learning.clickstream.edge.auth;

import lombok.ToString;
import lombok.Value;

/**
 * What POST /v1/token returns. The compact JWT is a secret for its whole TTL.
 * toString skips it so an access log of this object does not publish a bearer token.
 */
@Value
public class AccessToken {
  @ToString.Exclude
  String token;
  int expiresInSeconds;
  Scope scope;
}
