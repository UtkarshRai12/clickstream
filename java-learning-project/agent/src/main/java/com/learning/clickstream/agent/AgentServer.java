package com.learning.clickstream.agent;

import com.learning.clickstream.agent.http.IncidentsServlet;
import com.learning.clickstream.agent.http.PageServlet;
import com.learning.clickstream.agent.http.ReportsServlet;
import java.time.Clock;
import java.util.Map;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The report page and the incident API. Loopback only, same posture as the
 * Grafana port. Tests pass port 0 and read the assigned port back.
 */
public final class AgentServer implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(AgentServer.class);
  private final Server server;
  private final int port;

  private AgentServer(Server server, int port) {
    this.server = server;
    this.port = port;
  }

  /**
   * The writer and the Langfuse exporter come from the environment.
   * No Gemini key means the local writer. Blank Langfuse keys send nothing.
   * Step 8: the model name, base URL, timeout, port, and database path now come
   * from agent.properties (or their env overrides) instead of constants.
   */
  public static AgentServer start(AgentConfig config, Map<String, String> env) {
    String apiKey = IncidentWriters.apiKey(env);
    IncidentWriter writer = IncidentWriters.create(
        apiKey, config.modelName(), config.modelBaseUrl(), config.modelTimeout(), 2);
    // Say which writer and exporter are live, so a missing key is obvious at boot.
    log.info("writer={} langfuse={}", writer.modelName(), blank(env.get("LANGFUSE_PUBLIC_KEY")) ? "off" : "on");
    IncidentService service = new IncidentService(
        new ReportStore(config.dbPath()),
        writer,
        LangfuseExporter.fromEnv(env),
        Clock.systemUTC());
    return start(service, config.httpPort());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
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

  /** Blocks the main thread until Jetty stops. The shutdown hook stops it on Ctrl+C. */
  public void join() throws InterruptedException {
    server.join();
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
