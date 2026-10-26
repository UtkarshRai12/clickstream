package com.learning.clickstream.edge.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiKeyFileTest {
  @TempDir
  Path temp;

  @Test
  void hashInTheFileMatchesThePlaintext() throws Exception {
    Path file = temp.resolve("api-keys.csv");
    Files.writeString(file, """
        # comment
        key_id,tenant_id,scopes,secret_sha256
        demo-ingest,tenant-a,INGEST|REPORT,%s
        """.formatted(SecretHashes.sha256Hex("ingest-dev-secret")));

    ApiKey key = ConfigApiKeyRepository.load(file).find("ingest-dev-secret").orElseThrow();

    assertEquals("demo-ingest", key.getKeyId());
    assertEquals(Set.of(Scope.INGEST, Scope.REPORT), key.getScopes());
    assertTrue(ConfigApiKeyRepository.load(file).find("nope").isEmpty());
  }

  @Test
  void duplicateKeyIdFailsStartup() throws Exception {
    Path file = temp.resolve("api-keys.csv");
    String hashA = SecretHashes.sha256Hex("one");
    String hashB = SecretHashes.sha256Hex("two");
    Files.writeString(file, """
        demo,tenant-a,INGEST,%s
        demo,tenant-a,REPORT,%s
        """.formatted(hashA, hashB));

    assertThrows(IllegalArgumentException.class, () -> ConfigApiKeyRepository.load(file));
  }
}
