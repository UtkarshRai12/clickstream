package com.learning.clickstream.edge.ingest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Remembers the last {@link IngressTriggers#WINDOW_ATTEMPTS} Accept results
 * and whether each signal is already over its line. A snapshot comes back
 * only when a signal goes from cold to hot. The dispatcher thread is the
 * only caller, so this memory is not locked.
 */
@Singleton
public final class HopGate {
  private final Clock clock;
  private final ArrayDeque<Attempt> ring = new ArrayDeque<>();
  private boolean queueHot;
  private boolean errorHot;
  private boolean meanHot;
  private boolean dropHot;

  @Inject
  public HopGate(Clock clock) {
    this.clock = clock;
  }

  /** One finished Accept call. A success clears the drop signal. */
  public Optional<Snapshot> onForward(boolean ok, String code, double seconds, int queueDepth) {
    push(new Attempt(ok, code, seconds));
    if (ok) {
      dropHot = false;
    }
    return evaluate(queueDepth, 0);
  }

  /**
   * This batch will not be sent again. The failed tries are already in the
   * ring via {@link #onForward}. This call only raises the drop signal.
   */
  public Optional<Snapshot> onDrop(int queueDepth) {
    return evaluate(queueDepth, 1);
  }

  private void push(Attempt attempt) {
    ring.addLast(attempt);
    while (ring.size() > IngressTriggers.WINDOW_ATTEMPTS) {
      ring.removeFirst();
    }
  }

  private Optional<Snapshot> evaluate(int queueDepth, int droppedNow) {
    int attempts = ring.size();
    int failures = 0;
    double seconds = 0;
    Map<String, Integer> errorsByCode = new LinkedHashMap<>();
    for (Attempt attempt : ring) {
      seconds += attempt.seconds;
      if (!attempt.ok) {
        failures++;
        errorsByCode.merge(attempt.code, 1, Integer::sum);
      }
    }
    double mean = attempts == 0 ? 0 : seconds / attempts;
    double ratio = attempts == 0 ? 0 : (double) failures / attempts;
    List<String> reasons = new ArrayList<>();

    boolean queueNow = queueDepth >= IngressTriggers.QUEUE_DEPTH;
    if (queueNow && !queueHot) {
      reasons.add("queue depth " + queueDepth + " crossed " + IngressTriggers.QUEUE_DEPTH
          + " (batches waiting to be sent to the ingest server)");
    }
    queueHot = queueNow;

    boolean errorNow = attempts >= IngressTriggers.MIN_ATTEMPTS && ratio >= IngressTriggers.ERROR_RATIO;
    if (errorNow && !errorHot) {
      reasons.add("gRPC error ratio " + num(ratio) + " crossed " + num(IngressTriggers.ERROR_RATIO)
          + " (" + failures + " failed of the last " + attempts + " attempts)");
    }
    errorHot = errorNow;

    boolean meanNow = attempts >= IngressTriggers.MIN_ATTEMPTS && mean >= IngressTriggers.MEAN_SECONDS;
    if (meanNow && !meanHot) {
      reasons.add("mean Accept time " + num(mean) + "s crossed " + whole(IngressTriggers.MEAN_SECONDS)
          + "s over the last " + attempts + " attempts");
    }
    meanHot = meanNow;

    // A retry is not a drop. Only the call that abandons the batch can raise this.
    if (droppedNow >= IngressTriggers.DROPPED_BATCHES && !dropHot) {
      reasons.add("dropped batches crossed " + IngressTriggers.DROPPED_BATCHES
          + " (this batch will not be sent again)");
    }
    if (droppedNow >= IngressTriggers.DROPPED_BATCHES) {
      dropHot = true;
    }

    if (reasons.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Snapshot(
        clock.instant(), queueDepth, attempts, failures, droppedNow, mean, errorsByCode, reasons));
  }

  private static String num(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  /** The mean limit is a whole second in the reason text: {@code 1s}, not {@code 1.00s}. */
  private static String whole(double value) {
    if (value == Math.rint(value)) {
      return Long.toString(Math.round(value));
    }
    return num(value);
  }

  private record Attempt(boolean ok, String code, double seconds) {}
}
