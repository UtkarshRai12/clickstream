package com.learning.clickstream.server.doris;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Doris DATETIME has no time zone. Every writer formats UTC the same way.
 * Phase 6 report filters must use this same clock.
 */
public final class DorisTime {
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private DorisTime() {}

  public static String format(Instant instant) {
    return FORMAT.format(instant);
  }
}
