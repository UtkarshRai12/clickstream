package com.learning.clickstream.edge.auth;

import java.util.Locale;

/**
 * What a token is allowed to do. A token carries one scope.
 * INGEST may post events. REPORT may read aggregates.
 * The API key can hold more than one, but the token the client receives is
 * narrowed to the scope it asked for.
 */
public enum Scope {
  INGEST,
  REPORT;

  /** Used while reading api-keys.csv. A bad row fails startup, not a request. */
  public static Scope fromConfig(String raw) {
    try {
      return Scope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("unknown scope " + raw);
    }
  }

  /** Used for the optional scope query param on POST /v1/token. */
  public static Scope fromRequest(String raw) {
    try {
      return Scope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ex) {
      throw new AuthRejection(400, "unknown_scope");
    }
  }
}
