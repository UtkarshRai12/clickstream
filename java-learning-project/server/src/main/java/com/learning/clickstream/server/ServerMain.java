package com.learning.clickstream.server;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the ingest server. Run from the java-learning-project directory:
 * mvn -pl server -am exec:java -Dexec.mainClass=com.learning.clickstream.server.ServerMain
 *
 * The logs directory is created before the first logger call so Logback can
 * open logs/server.json. Alloy tails that file.
 */
public final class ServerMain {
  private ServerMain() {}

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Path.of("logs"));
    Logger log = LoggerFactory.getLogger(ServerMain.class);
    Path config = Path.of(args.length == 0 ? "config/application.properties" : args[0]);
    IngestServer server = IngestServer.start(ServerConfig.load(config));
    Runtime.getRuntime().addShutdownHook(new Thread(server::close, "server-shutdown"));
    log.info("ingest gRPC listening on 127.0.0.1:{}", server.port());
    Thread.currentThread().join();
  }
}
