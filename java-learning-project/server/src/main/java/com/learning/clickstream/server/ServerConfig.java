package com.learning.clickstream.server;

import com.learning.clickstream.server.doris.DorisAccessException;
import com.learning.clickstream.server.doris.DorisConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import lombok.Builder;
import lombok.Value;

/**
 * What the ingest server needs. Doris settings come from the phase 2 loader.
 * The internal token must match the edge. grpc.port 0 lets a test bind a free port.
 */
@Value
@Builder
public class ServerConfig {
  DorisConfig doris;
  int grpcPort;
  String internalToken;
  int flushSize;
  int flushIntervalMs;

  /** Prometheus scrape port. 0 binds a free port for tests. The laptop default is 9465. */
  @Builder.Default
  int metricsPort = 0;

  public static ServerConfig load(Path file) {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(file)) {
      properties.load(reader);
    } catch (IOException ex) {
      throw new DorisAccessException("could not read " + file, ex);
    }
    String token = properties.getProperty("internal.token", "").trim();
    if (token.isBlank()) {
      throw new DorisAccessException("missing internal.token");
    }
    return ServerConfig.builder()
        .doris(DorisConfig.load(file))
        .grpcPort(optionalInt(properties, "grpc.port", 7443))
        .internalToken(token)
        .flushSize(optionalInt(properties, "ingest.flush-size", 50))
        .flushIntervalMs(optionalInt(properties, "ingest.flush-interval-ms", 500))
        .metricsPort(optionalInt(properties, "server.metrics.port", 9465))
        .build();
  }

  private static int optionalInt(Properties properties, String key, int fallback) {
    String raw = properties.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value < 0) {
        throw new DorisAccessException(key + " must be >= 0");
      }
      return value;
    } catch (NumberFormatException ex) {
      throw new DorisAccessException(key + " is not an integer: " + raw);
    }
  }
}
