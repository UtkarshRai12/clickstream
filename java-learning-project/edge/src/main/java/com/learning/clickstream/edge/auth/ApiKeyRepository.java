package com.learning.clickstream.edge.auth;

import java.util.Optional;

/** Looks up a key from the plaintext a client sent. Implementations store only hashes. */
public interface ApiKeyRepository {
  Optional<ApiKey> find(String presentedSecret);
}
