package com.learning.clickstream.server.doris;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Phase 2 exit check against the Doris container on this machine. */
class DorisProbeTest {

  @Test
  @Timeout(90)
  void probeRowIsVisibleThroughJdbc() {
    String eventId = new DorisProbe().run(DorisConfig.local());
    assertTrue(eventId.startsWith("probe-"));
  }
}
