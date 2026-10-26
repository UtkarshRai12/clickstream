package com.learning.clickstream.server;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.learning.clickstream.server.doris.DorisAccessException;
import com.learning.clickstream.server.doris.DorisDataSource;
import com.learning.clickstream.server.doris.SchemaBootstrap;
import com.learning.clickstream.server.grpc.IngestGrpcService;
import com.learning.clickstream.server.grpc.ServiceAuthInterceptor;
import com.learning.clickstream.server.ingest.IngestFlusher;
import com.learning.clickstream.server.metrics.ServerMetrics;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * The ingest process. It listens for the Accept stream and flushes into Doris.
 * Plaintext is for this laptop. The internal token is the only check on the gRPC port.
 * Metrics listen on their own port and are not part of that RPC.
 */
public final class IngestServer implements AutoCloseable {
  private final Server grpc;
  private final IngestFlusher flusher;
  private final ServerMetrics metrics;
  private final int port;

  private IngestServer(Server grpc, IngestFlusher flusher, ServerMetrics metrics, int port) {
    this.grpc = grpc;
    this.flusher = flusher;
    this.metrics = metrics;
    this.port = port;
  }

  public static IngestServer start(ServerConfig config) {
    applySchema(config);
    Injector injector = Guice.createInjector(new ServerModule(config));
    IngestFlusher flusher = injector.getInstance(IngestFlusher.class);
    ServerMetrics metrics = injector.getInstance(ServerMetrics.class);
    flusher.start();
    Server grpc = NettyServerBuilder
        .forAddress(new InetSocketAddress("127.0.0.1", config.getGrpcPort()))
        .addService(ServerInterceptors.intercept(
            injector.getInstance(IngestGrpcService.class),
            injector.getInstance(ServiceAuthInterceptor.class)))
        .build();
    try {
      grpc.start();
      metrics.start(config.getMetricsPort());
    } catch (IOException ex) {
      metrics.close();
      grpc.shutdownNow();
      flusher.close();
      throw new DorisAccessException("ingest gRPC server failed to start", ex);
    }
    return new IngestServer(grpc, flusher, metrics, grpc.getPort());
  }

  private static void applySchema(ServerConfig config) {
    DorisDataSource dataSource = new DorisDataSource(config.getDoris());
    try (Connection connection = dataSource.open()) {
      new SchemaBootstrap().apply(connection);
    } catch (SQLException ex) {
      throw new DorisAccessException("Doris schema failed before the gRPC port opened", ex);
    }
  }

  public int port() {
    return port;
  }

  @Override
  public void close() {
    grpc.shutdown();
    try {
      grpc.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    flusher.close();
    metrics.close();
  }
}
