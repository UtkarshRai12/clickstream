package com.learning.clickstream.agent;

import com.learning.clickstream.agent.http.IncidentsServlet;
import com.learning.clickstream.agent.http.PageServlet;
import com.learning.clickstream.agent.http.ReportsServlet;
import java.nio.file.Path;
import java.time.Clock;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/**
 * The report page and the incident API. Loopback only, same posture as the
 * Grafana port. Tests pass port 0 and read the assigned port back.
 */
public final class AgentServer implements AutoCloseable {
  private final Server server;
  private final int port;

  private AgentServer(Server server, int port) {
    this.server = server;
    this.port = port;
  }

  /** A local writer and an exporter that drops spans. Boot uses this until a key exists. */
  public static AgentServer start(Path database, int port) {
    IncidentService service = new IncidentService(
        new ReportStore(database),
        new LocalIncidentWriter(),
        spans -> { },
        Clock.systemUTC());
    return start(service, port);
  }

  public static AgentServer start(IncidentService service, int port) {
    Server server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setHost("127.0.0.1");
    connector.setPort(port);
    server.addConnector(connector);

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    context.addServlet(new ServletHolder(new PageServlet()), "/");
    context.addServlet(new ServletHolder(new IncidentsServlet(service)), "/api/incidents");
    // Two holders, one service. The exact path is the list. The star path is one report.
    context.addServlet(new ServletHolder(new ReportsServlet(service)), "/api/reports");
    context.addServlet(new ServletHolder(new ReportsServlet(service)), "/api/reports/*");
    server.setHandler(context);
    try {
      server.start();
    } catch (Exception ex) {
      throw new IllegalStateException("agent failed to start", ex);
    }
    return new AgentServer(server, connector.getLocalPort());
  }

  public int port() {
    return port;
  }

  @Override
  public void close() {
    try {
      server.stop();
    } catch (Exception ex) {
      throw new IllegalStateException("agent failed to stop", ex);
    }
  }
}
