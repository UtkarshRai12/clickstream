package com.learning.clickstream.edge.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Posts a snapshot to the agent after the dispatcher has moved on.
 * One daemon thread does the HTTP call. A miss is logged and not retried.
 */
@Singleton
public final class AgentNotifier implements AutoCloseable {
  /** Same host and port as the report page. */
  public static final URI INCIDENTS = URI.create("http://127.0.0.1:8095/api/incidents");

  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final Logger log = LoggerFactory.getLogger(AgentNotifier.class);

  private final URI target;
  private final ObjectMapper json = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final BlockingQueue<Snapshot> pending = new LinkedBlockingQueue<>();
  private final ExecutorService httpThreads;
  private final HttpClient client;
  private final Thread thread;
  private volatile boolean running = true;

  @Inject
  public AgentNotifier() {
    this(INCIDENTS);
  }

  /** Tests pass a recording server. The running edge uses {@link #INCIDENTS}. */
  public AgentNotifier(URI target) {
    this.target = target;
    httpThreads = Executors.newCachedThreadPool(daemon("agent-notifier-http"));
    client = HttpClient.newBuilder().connectTimeout(TIMEOUT).executor(httpThreads).build();
    thread = new Thread(this::loop, "agent-notifier");
    thread.setDaemon(true);
    thread.start();
  }

  /** Queues the snapshot and returns. The dispatcher does not wait for the POST. */
  public void submit(Snapshot snapshot) {
    pending.offer(snapshot);
  }

  private void loop() {
    while (running) {
      try {
        post(pending.take());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void post(Snapshot snapshot) {
    try {
      HttpRequest request = HttpRequest.newBuilder(target)
          .timeout(TIMEOUT)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(snapshot)))
          .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("agent rejected the snapshot status={}", response.statusCode());
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      // The edge keeps accepting events. The crossing is already in the log line.
      log.warn("agent missed the snapshot: {}", ex.toString());
    }
  }

  @Override
  public void close() {
    running = false;
    thread.interrupt();
    httpThreads.shutdownNow();
  }

  private static ThreadFactory daemon(String name) {
    return task -> {
      Thread thread = new Thread(task, name);
      thread.setDaemon(true);
      return thread;
    };
  }
}
