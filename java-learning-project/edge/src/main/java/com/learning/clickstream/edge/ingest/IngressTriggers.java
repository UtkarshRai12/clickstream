package com.learning.clickstream.edge.ingest;

/**
 * Lines the edge compares against the Accept hop. These are constants.
 * The agent does not configure them, and nothing polls for them.
 */
public final class IngressTriggers {
  /** Batches waiting for the dispatcher. */
  public static final int QUEUE_DEPTH = 100;

  /** Failed attempts divided by attempts in the ring. */
  public static final double ERROR_RATIO = 0.25;

  /** The ratio and the mean stay quiet until the ring holds at least this many results. */
  public static final int MIN_ATTEMPTS = 5;

  /** How many recent Accept results the ring keeps. A count of RPCs, not a clock. */
  public static final int WINDOW_ATTEMPTS = 20;

  /** One batch the dispatcher will not send again. */
  public static final int DROPPED_BATCHES = 1;

  /** Mean Accept time, in seconds, across the ring. The RPC deadline is 5 seconds. */
  public static final double MEAN_SECONDS = 1;

  private IngressTriggers() {}
}
