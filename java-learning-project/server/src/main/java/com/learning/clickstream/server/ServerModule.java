package com.learning.clickstream.server;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.learning.clickstream.server.doris.DorisConfig;
import com.learning.clickstream.server.doris.DorisStreamLoader;

/**
 * Phase 5 server graph. Guice builds the buffer, the gRPC service, and the flusher.
 * IngestServer starts the flusher thread and the port after the injector exists.
 */
public final class ServerModule extends AbstractModule {
  private final ServerConfig config;

  public ServerModule(ServerConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(ServerConfig.class).toInstance(config);
    bind(DorisConfig.class).toInstance(config.getDoris());
  }

  @Provides
  @Singleton
  DorisStreamLoader streamLoader(DorisConfig doris) {
    return new DorisStreamLoader(doris);
  }
}
