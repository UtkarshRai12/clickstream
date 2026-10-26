package com.learning.clickstream.edge.auth;

import java.util.Set;
import lombok.ToString;
import lombok.Value;

/**
 * A loaded key. secretHash is the SHA-256 hex of the plaintext. The plaintext
 * is never stored. toString skips the hash so a log of this object is not a
 * lookup table for the file.
 */
@Value
public class ApiKey {
  String keyId;
  String tenantId;
  @ToString.Exclude
  String secretHash;
  Set<Scope> scopes;
}
