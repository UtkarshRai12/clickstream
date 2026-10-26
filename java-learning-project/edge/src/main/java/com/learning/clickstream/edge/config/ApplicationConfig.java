package com.learning.clickstream.edge.config;

import java.nio.file.Path;
import lombok.Builder;
import lombok.Value;

/** Values the edge needs to boot. Loaded from config/application.properties. */
@Value
@Builder
public class ApplicationConfig {
  int httpPort;
  String jwtHmacSecret;
  int ingestTtlSeconds;
  int reportTtlSeconds;
  Path apiKeyFile;

  /** Host the edge dials for the Accept RPC. */
  @Builder.Default
  String grpcTarget = "127.0.0.1:7443";

  /** Shared with the ingest server. This is not the public API key. */
  @Builder.Default
  String internalToken = "dev-internal-token-change-me";

  @Builder.Default
  int ingestQueueCapacity = 1000;

  @Builder.Default
  int ingestBatchCap = 500;

  /**
   * Prometheus scrape port. 0 asks the OS for a free port, which is what tests use.
   * 9464 is the laptop default. This port is not the public API.
   */
  @Builder.Default
  int metricsPort = 0;
}
