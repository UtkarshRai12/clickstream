package com.learning.clickstream.edge.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** One crossed snapshot becomes one POST. The notifier does not open the agent itself. */
class AgentNotifierTest {
  @Test
  void submitPostsTheSnapshotOnce() throws Exception {
    List<String> bodies = new CopyOnWriteArrayList<>();
    CountDownLatch posted = new CountDownLatch(1);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/incidents", exchange -> {
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
      posted.countDown();
    });
    server.start();
    AgentNotifier notifier = new AgentNotifier(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/incidents"));
    try {
      notifier.submit(new Snapshot(
          Instant.parse("2026-11-04T12:00:00Z"),
          150, 20, 5, 0, 1.4,
          Map.of("UNAVAILABLE", 5),
          List.of("queue depth 150 crossed 100 (batches waiting to be sent to the ingest server)")));
      assertTrue(posted.await(3, TimeUnit.SECONDS));
      assertEquals(1, bodies.size());
      assertTrue(bodies.get(0).contains("\"queueDepth\":150"));
      assertTrue(bodies.get(0).contains("queue depth 150 crossed 100"));
    } finally {
      notifier.close();
      server.stop(0);
    }
  }
}
