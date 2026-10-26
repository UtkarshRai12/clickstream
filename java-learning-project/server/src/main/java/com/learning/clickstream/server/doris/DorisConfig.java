package com.learning.clickstream.server.doris;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import lombok.Builder;
import lombok.Value;

/**
 * How this process reaches the Doris container that is already running.
 * Phase 2 reads these values. Guice will provide this object in phase 5.
 * Until then callers pass it into constructors.
 */
@Value
@Builder
public class DorisConfig {
  String jdbcUrl;
  String feHttp;
  String beHttp;
  String user;
  String password;

  /** Defaults that match config/application.properties on this machine. */
  public static DorisConfig local() {
    return DorisConfig.builder()
        .jdbcUrl("jdbc:mysql://127.0.0.1:9030/?useSSL=false&allowPublicKeyRetrieval=true"
            + "&characterEncoding=utf8&serverTimezone=UTC&useServerPrepStmts=false")
        .feHttp("http://127.0.0.1:8030")
        .beHttp("http://127.0.0.1:8040")
        .user("root")
        .password("")
        .build();
  }

  public static DorisConfig load(Path file) {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(file)) {
      properties.load(reader);
    } catch (IOException ex) {
      throw new DorisAccessException("could not read " + file, ex);
    }
    DorisConfig config = DorisConfig.builder()
        .jdbcUrl(required(properties, "doris.jdbc-url"))
        .feHttp(required(properties, "doris.fe-http"))
        .beHttp(required(properties, "doris.be-http"))
        .user(required(properties, "doris.user"))
        .password(properties.getProperty("doris.password", ""))
        .build();
    requireHttpPort(config.feHttp, "doris.fe-http");
    requireHttpPort(config.beHttp, "doris.be-http");
    return config;
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new DorisAccessException("missing " + key);
    }
    return value.trim();
  }

  /** Stream load builds URLs from these. A missing port would hit the wrong process. */
  private static void requireHttpPort(String raw, String key) {
    URI uri = URI.create(raw);
    if (uri.getHost() == null || uri.getPort() < 0) {
      throw new DorisAccessException(key + " must be an absolute URL with a port, got " + raw);
    }
  }
}
