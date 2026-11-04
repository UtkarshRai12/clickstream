package com.learning.clickstream.edge.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The gate returns a snapshot only when a hardcoded line is crossed from below. */
class HopGateTest {
  private static final Instant WHEN = Instant.parse("2026-11-04T12:00:00Z");

  private final HopGate gate = new HopGate(fixed());

  @Test
  void queueOf100Fires() {
    Snapshot snapshot = gate.onForward(true, "OK", 0.01, 100).orElseThrow();
    assertEquals(WHEN, snapshot.occurredAt());
    assertEquals(100, snapshot.queueDepth());
    assertTrue(snapshot.reasons().get(0).startsWith("queue depth 100 crossed 100"));
  }

  @Test
  void ratioStaysQuietUnderFiveAttempts() {
    for (int i = 0; i < 4; i++) {
      assertTrue(gate.onForward(false, "UNAVAILABLE", 0.01, 0).isEmpty());
    }
  }

  @Test
  void ratioFiresOnTheFullRing() {
    for (int i = 0; i < 20; i++) {
      assertTrue(gate.onForward(true, "OK", 0.01, 0).isEmpty());
    }
    for (int i = 0; i < 4; i++) {
      assertTrue(gate.onForward(false, "UNAVAILABLE", 0.01, 0).isEmpty());
    }
    Snapshot snapshot = gate.onForward(false, "UNAVAILABLE", 0.01, 0).orElseThrow();
    assertEquals(20, snapshot.attempts());
    assertEquals(5, snapshot.failures());
    assertEquals(5, snapshot.errorsByCode().get("UNAVAILABLE"));
    assertTrue(snapshot.reasons().get(0).contains("0.25"));
    assertTrue(snapshot.reasons().get(0).contains("5 failed of the last 20 attempts"));
  }

  @Test
  void oneDropFires() {
    Snapshot snapshot = gate.onDrop(0).orElseThrow();
    assertEquals(1, snapshot.dropped());
    assertTrue(snapshot.reasons().get(0).contains("dropped batches crossed 1"));
  }

  @Test
  void meanOverOneSecondFires() {
    for (int i = 0; i < 4; i++) {
      assertTrue(gate.onForward(true, "OK", 1.4, 0).isEmpty());
    }
    Snapshot snapshot = gate.onForward(true, "OK", 1.4, 0).orElseThrow();
    assertEquals(1.4, snapshot.meanSeconds(), 0.0001);
    assertTrue(snapshot.reasons().get(0).contains("mean Accept time 1.40s crossed 1s"));
    assertTrue(snapshot.reasons().get(0).contains("last 5 attempts"));
  }

  @Test
  void secondCallWhileHotDoesNotFire() {
    assertTrue(gate.onForward(true, "OK", 0.01, 100).isPresent());
    assertTrue(gate.onForward(true, "OK", 0.01, 150).isEmpty());
  }

  @Test
  void fallThenRiseFiresAgain() {
    assertTrue(gate.onForward(true, "OK", 0.01, 100).isPresent());
    assertTrue(gate.onForward(true, "OK", 0.01, 99).isEmpty());
    assertTrue(gate.onForward(true, "OK", 0.01, 100).isPresent());
  }

  @Test
  void twoSignalsOnOneCallProduceOneSnapshot() {
    for (int i = 0; i < 20; i++) {
      gate.onForward(true, "OK", 0.01, 0);
    }
    for (int i = 0; i < 4; i++) {
      assertTrue(gate.onForward(false, "UNAVAILABLE", 0.01, 0).isEmpty());
    }
    Snapshot snapshot = gate.onForward(false, "UNAVAILABLE", 0.01, 100).orElseThrow();
    assertEquals(2, snapshot.reasons().size());
    assertTrue(snapshot.reasons().get(0).contains("queue depth 100"));
    assertTrue(snapshot.reasons().get(1).contains("error ratio"));
  }

  private static Clock fixed() {
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return WHEN;
      }
    };
  }
}
