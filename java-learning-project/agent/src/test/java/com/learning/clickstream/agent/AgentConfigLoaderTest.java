package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Defaults, then the file, then the environment. The last one set wins. */
class AgentConfigLoaderTest {
  @TempDir Path dir;

  @Test
  void missingFileGivesTheDefaults() {
    AgentConfig config = AgentConfigLoader.load(dir.resolve("none.properties"), Map.of());

    assertEquals(8095, config.httpPort());
    assertEquals(Path.of("data/ingress-reports.db"), config.dbPath());
    assertEquals("", config.modelBaseUrl());
    assertEquals("gemini-2.5-flash", config.modelName());
    assertEquals(Duration.ofSeconds(30), config.modelTimeout());
  }

  @Test
  void fileOverridesDefaultsAndEnvOverridesFile() throws Exception {
    Path file = dir.resolve("agent.properties");
    Files.writeString(file, "agent.http.port=9000\nagent.model.name=from-file\n");

    // A blank env value counts as unset, so the file value stays.
    AgentConfig config = AgentConfigLoader.load(file, Map.of(
        "AGENT_HTTP_PORT", "9100",
        "AGENT_MODEL_NAME", " "));

    assertEquals(9100, config.httpPort());
    assertEquals("from-file", config.modelName());
  }

  @Test
  void badNumberNamesTheKey() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> AgentConfigLoader.load(dir.resolve("none"), Map.of("AGENT_HTTP_PORT", "abc")));

    assertEquals("agent.http.port must be a whole number, got: abc", ex.getMessage());
  }
}
