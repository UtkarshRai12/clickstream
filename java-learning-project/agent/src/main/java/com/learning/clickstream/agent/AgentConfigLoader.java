package com.learning.clickstream.agent;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Defaults, then config/agent.properties, then the environment.
 * An environment variable wins when it is set. The name is the key in upper case
 * with dots turned into underscores: agent.http.port is AGENT_HTTP_PORT.
 */
public final class AgentConfigLoader {
  public static final Path DEFAULT_FILE = Path.of("config/agent.properties");

  private AgentConfigLoader() {}

  public static AgentConfig load(Path file, Map<String, String> env) {
    Properties props = defaults();
    // A missing file is fine. The defaults are enough to start on a laptop.
    if (Files.exists(file)) {
      try (Reader reader = Files.newBufferedReader(file)) {
        props.load(reader);
      } catch (IOException ex) {
        throw new UncheckedIOException("could not read " + file, ex);
      }
    }
    return new AgentConfig(
        number(value(props, env, "agent.http.port"), "agent.http.port"),
        Path.of(value(props, env, "agent.db.path")),
        value(props, env, "agent.model.base.url"),
        value(props, env, "agent.model.name"),
        Duration.ofSeconds(number(value(props, env, "agent.model.timeout.seconds"), "agent.model.timeout.seconds")),
        value(props, env, "agent.gcp.project"),
        value(props, env, "agent.gcp.location"));
  }

  private static Properties defaults() {
    Properties props = new Properties();
    props.setProperty("agent.http.port", "8095");
    props.setProperty("agent.db.path", "data/ingress-reports.db");
    props.setProperty("agent.model.base.url", "");
    props.setProperty("agent.model.name", IncidentWriters.DEFAULT_MODEL);
    props.setProperty("agent.model.timeout.seconds", "30");
    // Empty project keeps the AI Studio key path. Current Gemini models use the global endpoint.
    props.setProperty("agent.gcp.project", "");
    props.setProperty("agent.gcp.location", "global");
    return props;
  }

  private static String value(Properties props, Map<String, String> env, String key) {
    String fromEnv = env.get(key.toUpperCase(Locale.ROOT).replace('.', '_'));
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv.trim();
    }
    return props.getProperty(key, "").trim();
  }

  private static int number(String raw, String key) {
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + " must be a whole number, got: " + raw, ex);
    }
  }
}
