package com.learning.clickstream.server.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DorisConfigTest {

  @Test
  void sampleFileMatchesTheLocalContainerDefaults() {
    DorisConfig loaded = DorisConfig.load(repoFile("config/application.properties"));
    DorisConfig local = DorisConfig.local();
    assertEquals(local.getJdbcUrl(), loaded.getJdbcUrl());
    assertEquals(local.getFeHttp(), loaded.getFeHttp());
    assertEquals(local.getBeHttp(), loaded.getBeHttp());
    assertEquals("root", loaded.getUser());
    assertEquals("", loaded.getPassword());
  }

  static Path repoFile(String relative) {
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
