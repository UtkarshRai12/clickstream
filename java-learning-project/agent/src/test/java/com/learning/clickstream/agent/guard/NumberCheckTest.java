package com.learning.clickstream.agent.guard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.agent.IncidentSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Numbers in the text have to come from the snapshot, or be 202. */
class NumberCheckTest {
  private static final IncidentSnapshot SNAPSHOT = new IncidentSnapshot(
      Instant.parse("2026-11-06T15:50:00Z"),
      0,
      3,
      3,
      1,
      0.0912,
      Map.of("UNAVAILABLE", 3),
      List.of("dropped batches crossed 1 (this batch will not be sent again)"));

  @Test
  void anExactCountPasses() {
    assertTrue(NumberCheck.passes("Dropped 1 batch.", SNAPSHOT));
  }

  @Test
  void aRoundedMeanPasses() {
    // 0.0912 rounded to two decimals is 0.09. That is the only slack.
    assertTrue(NumberCheck.passes("Mean Accept time 0.09 seconds.", SNAPSHOT));
  }

  @Test
  void aThresholdCopiedFromTheReasonPasses() {
    assertTrue(NumberCheck.passes("dropped batches crossed 1", SNAPSHOT));
  }

  @Test
  void theAcceptedStatusIsAllowed() {
    assertTrue(NumberCheck.passes("The edge already returned 202.", SNAPSHOT));
  }

  @Test
  void anUnknownNumberFails() {
    assertFalse(NumberCheck.passes("Dropped 7 batches.", SNAPSHOT));
  }
}
