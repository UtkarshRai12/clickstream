package com.learning.clickstream.agent.guard;

import com.learning.clickstream.agent.FollowUpQuery;
import com.learning.clickstream.agent.IncidentSnapshot;
import com.learning.clickstream.agent.IncidentWriter;
import com.learning.clickstream.agent.LocalIncidentWriter;
import com.learning.clickstream.agent.WriterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks the model text before it is stored. One retry with the same prompt,
 * then the local writer. The retry cannot be told what failed: the writer
 * interface has no place for that. The fallback is the real protection.
 */
public final class GuardedWriter implements IncidentWriter {
  private static final Logger log = LoggerFactory.getLogger(GuardedWriter.class);

  private final IncidentWriter model;
  private final LocalIncidentWriter local = new LocalIncidentWriter();

  public GuardedWriter(IncidentWriter model) {
    this.model = model;
  }

  @Override
  public String modelName() {
    return model.modelName();
  }

  @Override
  public WriterResult writeReport(IncidentSnapshot snapshot) {
    return checked(snapshot, () -> model.writeReport(snapshot), () -> local.writeReport(snapshot));
  }

  @Override
  public WriterResult answer(FollowUpQuery query) {
    return checked(query.snapshot(), () -> model.answer(query), () -> local.answer(query));
  }

  private WriterResult checked(
      IncidentSnapshot snapshot,
      Call call,
      Call fallback) {
    WriterResult first = call.get();
    if (NumberCheck.passes(first.text(), snapshot)) {
      return first;
    }
    // Same input again. A second invented number is not stored.
    WriterResult second = call.get().plusTokens(first);
    if (NumberCheck.passes(second.text(), snapshot)) {
      return second;
    }
    log.info("guardrail numbers_grounded failed, used local writer");
    return fallback.get().keepingSpendFrom(second);
  }

  @FunctionalInterface
  private interface Call {
    WriterResult get();
  }
}
