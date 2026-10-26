package com.learning.clickstream.edge.config;

import com.learning.clickstream.edge.auth.HmacSecrets;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads the edge keys from the shared properties file.
 * Doris keys in the same file are ignored. The server has its own loader.
 * A relative api-key-file is resolved next to the properties file, not against
 * the process working directory, so the edge can be started from another folder
 * as long as the argument points at the properties file.
 */
public final class ApplicationConfigLoader {
  private ApplicationConfigLoader() {}

  public static ApplicationConfig load(Path file) {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(file)) {
      properties.load(reader);
    } catch (IOException ex) {
      throw new UncheckedIOException("could not read " + file, ex);
    }
    int ingestTtl = positiveInt(properties, "jwt.ingest-ttl-seconds");
    int reportTtl = positiveInt(properties, "jwt.report-ttl-seconds");
    Path keyFile = file.getParent().resolve(required(properties, "api-key-file")).normalize();
    return ApplicationConfig.builder()
        .httpPort(positiveInt(properties, "http.port"))
        .jwtHmacSecret(HmacSecrets.require(required(properties, "jwt.hmac-secret")))
        .ingestTtlSeconds(ingestTtl)
        .reportTtlSeconds(reportTtl)
        .apiKeyFile(keyFile)
        .grpcTarget(optional(properties, "grpc.target", "127.0.0.1:7443"))
        .internalToken(required(properties, "internal.token"))
        .ingestQueueCapacity(positiveInt(properties, "ingest.queue-capacity"))
        .ingestBatchCap(positiveInt(properties, "ingest.batch-cap"))
        .metricsPort(nonNegativeInt(properties, "edge.metrics.port", 9464))
        .build();
  }

  private static String optional(Properties properties, String key, String fallback) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("missing " + key);
    }
    return value.trim();
  }

  /** metrics.port may be 0 so a test can bind a free port. A missing key uses the fallback. */
  private static int nonNegativeInt(Properties properties, String key, int fallback) {
    String raw = properties.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value < 0) {
        throw new IllegalArgumentException(key + " must be >= 0");
      }
      return value;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + " is not an integer: " + raw);
    }
  }

  private static int positiveInt(Properties properties, String key) {
    String raw = required(properties, key);
    try {
      int value = Integer.parseInt(raw);
      if (value <= 0) {
        throw new IllegalArgumentException(key + " must be positive");
      }
      return value;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + " is not an integer: " + raw);
    }
  }
}
