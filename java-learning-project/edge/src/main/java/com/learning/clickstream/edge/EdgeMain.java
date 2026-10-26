package com.learning.clickstream.edge;

import com.learning.clickstream.edge.config.ApplicationConfig;
import com.learning.clickstream.edge.config.ApplicationConfigLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the edge. Run from the java-learning-project directory:
 * mvn -pl edge -am exec:java -Dexec.mainClass=com.learning.clickstream.edge.EdgeMain
 *
 * The logs directory is created before the first logger call so Logback can
 * open logs/edge.json. Alloy tails that file.
 */
public final class EdgeMain {
  private EdgeMain() {}

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Path.of("logs"));
    Logger log = LoggerFactory.getLogger(EdgeMain.class);
    Path configPath = Path.of(args.length == 0 ? "config/application.properties" : args[0]);
    ApplicationConfig config = ApplicationConfigLoader.load(configPath);
    EdgeServer server = EdgeServer.start(config, Clock.systemUTC());
    Runtime.getRuntime().addShutdownHook(new Thread(server::close, "edge-shutdown"));
    log.info("edge listening on http://127.0.0.1:{}", server.port());
    Thread.currentThread().join();
  }
}
