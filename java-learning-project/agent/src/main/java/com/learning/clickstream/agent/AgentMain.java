package com.learning.clickstream.agent;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the ingress agent. Run it from java-learning-project so config/, data/,
 * and logs/ resolve:
 *
 *   mvn -pl agent -am exec:java -Dexec.mainClass=com.learning.clickstream.agent.AgentMain
 *
 * The agent waits. It does nothing until the edge posts a crossed snapshot.
 */
public final class AgentMain {
  private static final Logger log = LoggerFactory.getLogger(AgentMain.class);

  private AgentMain() {}

  public static void main(String[] args) throws Exception {
    Map<String, String> env = System.getenv();
    AgentConfig config = AgentConfigLoader.load(AgentConfigLoader.DEFAULT_FILE, env);
    AgentServer server = AgentServer.start(config, env);
    Runtime.getRuntime().addShutdownHook(new Thread(server::close, "agent-shutdown"));
    log.info("ingress agent on http://127.0.0.1:{}/ db={}", server.port(), config.dbPath().toAbsolutePath());
    server.join();
  }
}
