package com.learning.clickstream.edge;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import com.learning.clickstream.edge.config.ApplicationConfig;
import com.learning.clickstream.edge.http.NotFoundServlet;
import com.learning.clickstream.edge.ingest.IngestDispatcher;
import com.learning.clickstream.edge.metrics.EdgeMetrics;
import io.grpc.ManagedChannel;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/**
 * Embedded Jetty. HTTP sessions are off because the JWT is the credential.
 * The server listens on 127.0.0.1 only. Tests pass port 0 and read the
 * assigned port back. The dispatcher starts after the port is open and stops
 * before the process exits. Batches still in the queue at shutdown are dropped.
 */
public final class EdgeServer implements AutoCloseable {
  private final Server server;
  private final IngestDispatcher dispatcher;
  private final ManagedChannel channel;
  private final EdgeMetrics metrics;
  private final int port;

  private EdgeServer(
      Server server, IngestDispatcher dispatcher, ManagedChannel channel, EdgeMetrics metrics, int port) {
    this.server = server;
    this.dispatcher = dispatcher;
    this.channel = channel;
    this.metrics = metrics;
    this.port = port;
  }

  public static EdgeServer start(ApplicationConfig config, Clock clock) {
    Injector injector = Guice.createInjector(new EdgeModule(config, clock));
    Server server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setHost("127.0.0.1");
    connector.setPort(config.getHttpPort());
    server.addConnector(connector);

    // No session handler. A cookie session would fight the stateless token.
    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    context.addEventListener(new EdgeGuiceContext(injector));
    context.addFilter(GuiceFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    context.addServlet(NotFoundServlet.class, "/*");
    server.setHandler(context);
    EdgeMetrics metrics = injector.getInstance(EdgeMetrics.class);
    try {
      server.start();
      // Separate port from Jetty so Prometheus is not behind the JWT filter.
      metrics.start(config.getMetricsPort());
    } catch (Exception ex) {
      metrics.close();
      try {
        server.stop();
      } catch (Exception ignored) {
        // The original failure is the one worth throwing.
      }
      throw new IllegalStateException("edge failed to start", ex);
    }
    IngestDispatcher dispatcher = injector.getInstance(IngestDispatcher.class);
    dispatcher.start();
    return new EdgeServer(
        server, dispatcher, injector.getInstance(ManagedChannel.class), metrics, connector.getLocalPort());
  }

  public int port() {
    return port;
  }

  public int metricsPort() {
    return metrics.port();
  }

  @Override
  public void close() {
    try {
      dispatcher.close();
      channel.shutdown();
      channel.awaitTermination(1, TimeUnit.SECONDS);
      server.stop();
      metrics.close();
    } catch (Exception ex) {
      throw new IllegalStateException("edge failed to stop", ex);
    }
  }
}
