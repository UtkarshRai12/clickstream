package com.learning.clickstream.edge.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads api-keys.csv once at startup.
 * Lookup hashes the presented secret and reads a map, so a wrong secret and a
 * right secret both pay for one SHA-256. The file is not a user database.
 * Rotating a key means editing the file and restarting the edge.
 */
@Slf4j
public final class ConfigApiKeyRepository implements ApiKeyRepository {
  private final Map<String, ApiKey> byHash;

  private ConfigApiKeyRepository(Map<String, ApiKey> byHash) {
    this.byHash = byHash;
  }

  public static ConfigApiKeyRepository load(Path file) {
    Map<String, ApiKey> byHash = new HashMap<>();
    Set<String> ids = new HashSet<>();
    int lineNumber = 0;
    try {
      for (String raw : Files.readAllLines(file)) {
        lineNumber++;
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("key_id,")) {
          continue;
        }
        ApiKey key = parse(line, lineNumber);
        if (!ids.add(key.getKeyId())) {
          throw new IllegalArgumentException("duplicate key id on line " + lineNumber);
        }
        if (byHash.putIfAbsent(key.getSecretHash(), key) != null) {
          throw new IllegalArgumentException("duplicate secret hash on line " + lineNumber);
        }
      }
    } catch (IOException ex) {
      throw new UncheckedIOException("could not read " + file, ex);
    }
    if (byHash.isEmpty()) {
      throw new IllegalArgumentException("no api keys in " + file);
    }
    log.info("loaded {} api keys from {}", byHash.size(), file);
    return new ConfigApiKeyRepository(Map.copyOf(byHash));
  }

  private static ApiKey parse(String line, int lineNumber) {
    String[] columns = line.split(",", -1);
    if (columns.length != 4) {
      throw new IllegalArgumentException("line " + lineNumber + " needs 4 columns");
    }
    String keyId = columns[0].trim();
    String tenantId = columns[1].trim();
    if (keyId.isEmpty() || tenantId.isEmpty()) {
      throw new IllegalArgumentException("line " + lineNumber + " is missing key id or tenant");
    }
    Set<Scope> scopes = new HashSet<>();
    for (String part : columns[2].split("\\|")) {
      if (!part.isBlank()) {
        scopes.add(Scope.fromConfig(part));
      }
    }
    if (scopes.isEmpty()) {
      throw new IllegalArgumentException("line " + lineNumber + " has no scopes");
    }
    String hash = columns[3].trim().toLowerCase(Locale.ROOT);
    if (!hash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("line " + lineNumber + " secret hash must be 64 hex chars");
    }
    return new ApiKey(keyId, tenantId, hash, Set.copyOf(scopes));
  }

  @Override
  public Optional<ApiKey> find(String presentedSecret) {
    if (presentedSecret == null || presentedSecret.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byHash.get(SecretHashes.sha256Hex(presentedSecret)));
  }
}
