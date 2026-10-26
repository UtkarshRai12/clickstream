package com.learning.clickstream.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The shared properties file gives the server its own scrape port. */
class ServerConfigTest {

  @Test
  void sampleFileUsesTheServerMetricsPort() {
    ServerConfig config = ServerConfig.load(repoFile("config/application.properties"));
    assertEquals(7443, config.getGrpcPort());
    assertEquals(9465, config.getMetricsPort());
  }

  private static Path repoFile(String relative) {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      Path candidate = dir.resolve(relative);
      if (candidate.toFile().isFile()) {
        return candidate;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("could not find " + relative);
  }
}
