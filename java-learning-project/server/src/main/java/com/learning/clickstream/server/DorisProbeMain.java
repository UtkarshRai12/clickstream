package com.learning.clickstream.server;

import com.learning.clickstream.server.doris.DorisConfig;
import com.learning.clickstream.server.doris.DorisProbe;
import java.nio.file.Path;

/**
 * Re-runs the phase 2 probe from the java-learning-project directory:
 * mvn -pl server exec:java -Dexec.mainClass=com.learning.clickstream.server.DorisProbeMain
 * or, after a package, the test DorisProbeTest is the same code path.
 */
public final class DorisProbeMain {
  private DorisProbeMain() {}

  public static void main(String[] args) {
    Path config = Path.of(args.length == 0 ? "config/application.properties" : args[0]);
    String eventId = new DorisProbe().run(DorisConfig.load(config));
    System.out.println("visible event_id=" + eventId);
  }
}
