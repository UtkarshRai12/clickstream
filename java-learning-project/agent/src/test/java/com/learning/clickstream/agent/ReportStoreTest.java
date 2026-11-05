package com.learning.clickstream.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Insert, newest-first list, append, and a missing id. No HTTP in this phase. */
class ReportStoreTest {
  private static final Instant OLDER = Instant.parse("2026-11-04T12:00:00Z");
  private static final Instant NEWER = Instant.parse("2026-11-04T12:05:00Z");

  @TempDir
  Path dir;

  private ReportStore store;

  @BeforeEach
  void open() {
    store = new ReportStore(dir.resolve("reports.db"));
  }

  @Test
  void insertKeepsTheBodyAsTheFirstMessage() {
    String body = "Queue is 150.\nDoris is a later hop.";
    StoredReport report = store.insert(
        "r-old", OLDER, List.of("queue depth 150 crossed 100"), "{\"queueDepth\":150}",
        body, "local", "a".repeat(32), "b".repeat(16));

    assertEquals(body, report.body());
    assertEquals(1, report.messages().size());
    assertEquals("assistant", report.messages().get(0).role());
    assertEquals(body, report.messages().get(0).content());
    assertEquals("Queue is 150.", store.list().get(0).preview());
  }

  @Test
  void listIsNewestFirstAndPreviewIsCut() {
    store.insert("r-old", OLDER, List.of("old"), "{}", "older body", "local", "a".repeat(32), "b".repeat(16));
    String longLine = "x".repeat(180);
    store.insert(
        "r-new", NEWER, List.of("queue depth 100 crossed 100"), "{}",
        longLine + "\nrest", "gemini-2.5-flash", "c".repeat(32), "d".repeat(16));

    List<ReportSummary> list = store.list();
    assertEquals(List.of("r-new", "r-old"), list.stream().map(ReportSummary::id).toList());
    assertEquals(140, list.get(0).preview().length());
    assertEquals("x".repeat(140), list.get(0).preview());
    assertEquals(List.of("queue depth 100 crossed 100"), list.get(0).triggers());
  }

  @Test
  void appendAddsATurnAfterTheReport() {
    store.insert("r-1", OLDER, List.of("drop"), "{}", "Dropped one batch.", "local",
        "a".repeat(32), "b".repeat(16));
    Instant asked = OLDER.plusSeconds(30);
    ChatMessage turn = store.append("r-1", "user", "Is the queue still high?", asked).orElseThrow();

    StoredReport report = store.find("r-1").orElseThrow();
    assertEquals(2, report.messages().size());
    assertEquals("assistant", report.messages().get(0).role());
    assertEquals(turn, report.messages().get(1));
  }

  @Test
  void missingIdIsEmpty() {
    assertTrue(store.find("missing").isEmpty());
    Optional<ChatMessage> appended = store.append("missing", "user", "hello", NEWER);
    assertTrue(appended.isEmpty());
    assertTrue(store.find("missing").isEmpty());
  }
}
