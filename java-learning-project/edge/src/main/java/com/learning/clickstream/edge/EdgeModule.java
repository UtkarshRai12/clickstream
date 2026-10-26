package com.learning.clickstream.edge;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletScopes;
import com.learning.clickstream.edge.auth.ApiKeyRepository;
import com.learning.clickstream.edge.auth.Caller;
import com.learning.clickstream.edge.auth.CallerFromRequest;
import com.learning.clickstream.edge.auth.ConfigApiKeyRepository;
import com.learning.clickstream.edge.auth.JwtTokenIssuer;
import com.learning.clickstream.edge.auth.JwtTokenVerifier;
import com.learning.clickstream.edge.auth.TokenIssuer;
import com.learning.clickstream.edge.auth.TokenVerifier;
import com.learning.clickstream.edge.config.ApplicationConfig;
import com.learning.clickstream.edge.ingest.AcceptedEventQueue;
import com.learning.clickstream.edge.ingest.GrpcIngestClient;
import com.learning.clickstream.edge.ingest.IngestClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Clock;

/**
 * Guice builds the object graph. A class asks for what it needs in its constructor.
 *
 * Wired: config, clock, API keys, JWT, the caller for the current request,
 * the ingest queue, and the plaintext gRPC channel to the ingest server.
 *
 * Not wired: report queries. Prometheus is a separate port, not a servlet.
 * The edge does not open a Doris connection.
 */
public final class EdgeModule extends AbstractModule {
  private final ApplicationConfig config;
  private final Clock clock;

  public EdgeModule(ApplicationConfig config, Clock clock) {
    this.config = config;
    this.clock = clock;
  }

  @Override
  protected void configure() {
    bind(ApplicationConfig.class).toInstance(config);
    bind(Clock.class).toInstance(clock);
    bind(TokenIssuer.class).to(JwtTokenIssuer.class);
    bind(TokenVerifier.class).to(JwtTokenVerifier.class);
    bind(IngestClient.class).to(GrpcIngestClient.class);
    // Request scope: one Caller per HTTP request, invisible to other requests.
    bind(Caller.class).toProvider(CallerFromRequest.class).in(ServletScopes.REQUEST);
    install(new EdgeServletModule());
  }

  @Provides
  @Singleton
  ApiKeyRepository apiKeys(ApplicationConfig config) {
    return ConfigApiKeyRepository.load(config.getApiKeyFile());
  }

  @Provides
  @Singleton
  AcceptedEventQueue acceptedEvents(ApplicationConfig config) {
    return new AcceptedEventQueue(config.getIngestQueueCapacity());
  }

  /** Local laptop only. The channel is plaintext and stays on 127.0.0.1. */
  @Provides
  @Singleton
  ManagedChannel ingestChannel(ApplicationConfig config) {
    return ManagedChannelBuilder.forTarget(config.getGrpcTarget()).usePlaintext().build();
  }
}
