package com.learning.clickstream.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.edge.auth.ApiKey;
import com.learning.clickstream.edge.auth.ApiKeyRepository;
import com.learning.clickstream.edge.auth.ConfigApiKeyRepository;
import com.learning.clickstream.edge.auth.Scope;
import com.learning.clickstream.edge.config.ApplicationConfig;
import com.learning.clickstream.edge.config.ApplicationConfigLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The plaintext secrets documented in the CSV must keep matching the stored hashes. */
class SampleConfigTest {

  @Test
  void documentedDevSecretsOpenTheSampleKeys() {
    Path properties = repoFile("config/application.properties");
    ApplicationConfig config = ApplicationConfigLoader.load(properties);
    ApiKeyRepository keys = ConfigApiKeyRepository.load(config.getApiKeyFile());

    ApiKey ingest = keys.find("ingest-dev-secret").orElseThrow();
    assertEquals("demo-ingest", ingest.getKeyId());
    assertEquals("tenant-a", ingest.getTenantId());
    assertTrue(ingest.getScopes().contains(Scope.INGEST));

    ApiKey report = keys.find("report-dev-secret").orElseThrow();
    assertEquals("demo-report", report.getKeyId());
    assertTrue(report.getScopes().contains(Scope.REPORT));
    assertEquals(8080, config.getHttpPort());
    assertEquals(9464, config.getMetricsPort());
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
