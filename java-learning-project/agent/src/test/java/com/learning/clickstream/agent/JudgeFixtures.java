package com.learning.clickstream.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends known reports to Langfuse so the snapshot_faithful judge can be checked.
 * Six are honest. Six each break one rule. The local writer is one of the honest
 * ones: if the judge fails that text, the judge prompt is wrong.
 *
 * From java-learning-project, with config/agent.env sourced. exec:java stays
 * on AgentMain, so this main is launched from the test classpath:
 * mvn -pl agent test-compile dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/agent-cp.txt
 * java -cp "agent/target/test-classes:agent/target/classes:$(cat /tmp/agent-cp.txt)" com.learning.clickstream.agent.JudgeFixtures
 */
public final class JudgeFixtures {
  private static final String SESSION = "judge-fixtures";

  private JudgeFixtures() {}

  public static void main(String[] args) {
    // The exporter drops the batch when the keys are blank. Fail here instead of printing ids for traces that were never sent.
    if (blank(System.getenv("LANGFUSE_PUBLIC_KEY")) || blank(System.getenv("LANGFUSE_SECRET_KEY"))) {
      System.err.println("Langfuse keys are blank. Source config/agent.env first.");
      System.exit(1);
    }
    IncidentSnapshot snapshot = sample();
    String json = IncidentText.toJson(snapshot);
    // Same text the fallback writer stores. A false score on this case means the prompt is biased.
    String local = new LocalIncidentWriter().writeReport(snapshot).text();
    // Pass case ids to resend only those. An empty args list sends all twelve.
    List<String> only = List.of(args);
    List<Fixture> fixtures = List.of(
        new Fixture("local", true, local),
        new Fixture("honest-counts", true, """
            Queue depth 4. Attempts 20, failures 3, dropped 1. Mean Accept time 0.09 seconds.
            The snapshot does not show why."""),
        new Fixture("honest-unavailable", true, """
            Status codes include UNAVAILABLE 1, so the server was unreachable.
            Dropped 1. The snapshot does not show why."""),
        new Fixture("honest-202", true, """
            The edge already returned 202. Dropped batches after that: 1. Attempts 20.
            The snapshot does not show why."""),
        new Fixture("honest-mean", true, """
            Queue depth is 4 and the mean Accept time is 0.09 seconds. Failures 3.
            The snapshot does not show why."""),
        new Fixture("honest-no-tenant", true, """
            Attempts 20, failures 3, dropped 1. No tenant or host is in the snapshot.
            The snapshot does not show why."""),
        new Fixture("changed-number", false, """
            Attempts 20, failures 3, dropped 7. The snapshot does not show why."""),
        new Fixture("doris", false, """
            Doris is down. Attempts 20, dropped 1. The snapshot does not show why."""),
        new Fixture("tenant", false, """
            This was tenant acme. Attempts 20, dropped 1. The snapshot does not show why."""),
        new Fixture("rejected", false, """
            The events were rejected. Attempts 20, dropped 1. The snapshot does not show why."""),
        new Fixture("fix", false, """
            Restart the ingest process to clear the queue depth of 4.
            Attempts 20, dropped 1."""),
        new Fixture("long-cause", false, """
            The ingress hop crossed a trigger. Queue depth was 4. There were 20 attempts,
            3 failures, and 1 dropped batch. Mean Accept time was 0.09 seconds.
            Status codes include UNAVAILABLE 1, so the server was unreachable for that one call.
            The edge already returned 202 for what it accepted.
            The numbers above are the whole snapshot. The root cause is a full disk."""));
    TraceExporter exporter = LangfuseExporter.fromEnv(System.getenv());
    Instant now = Instant.now();
    int n = 0;
    for (Fixture fixture : fixtures) {
      if (!only.isEmpty() && !only.contains(fixture.id)) {
        n++;
        continue;
      }
      String traceId = id(32, n);
      exporter.export(List.of(root(traceId, id(16, n), json, fixture, now)));
      // The compare step matches this trace id to the score Langfuse writes later.
      System.out.println(fixture.id + " expected=" + fixture.expected + " trace=" + traceId);
      n++;
    }
  }

  /** One root observation. The name is what the judge rule filters on. */
  private static Observation root(String traceId, String spanId, String json, Fixture fixture, Instant now) {
    return new Observation(
        "ingress-incident",
        traceId,
        spanId,
        null,
        SESSION,
        "ingress-incident",
        "agent",
        "local",
        json,
        fixture.text,
        now,
        now,
        Map.of("fixture", "true", "expected", Boolean.toString(fixture.expected), "case", fixture.id),
        null,
        null,
        null,
        null);
  }

  private static IncidentSnapshot sample() {
    return new IncidentSnapshot(
        Instant.parse("2026-11-08T18:00:00Z"),
        4,
        20,
        3,
        1,
        0.0912,
        Map.of("UNAVAILABLE", 1),
        List.of("the batch was dropped after the Accept calls failed"));
  }

  /** Lowercase hex, 16 or 32 chars, unique per process and case. */
  private static String id(int width, int n) {
    String tail = String.format("%08x%02x", System.currentTimeMillis() / 1000, n);
    return "0".repeat(width - tail.length()) + tail;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private record Fixture(String id, boolean expected, String text) {}
}
