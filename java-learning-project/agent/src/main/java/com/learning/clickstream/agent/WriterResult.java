package com.learning.clickstream.agent;

/**
 * What one writer call produced. {@code model} is what gets stored on the row.
 * {@code billedModel} is the model that actually ran, which differs when the
 * guardrail throws the model text away and keeps the local text. Token counts
 * stay, because those calls were still spent.
 */
public record WriterResult(
    String text,
    String model,
    String billedModel,
    Integer inputTokens,
    Integer outputTokens,
    String promptName,
    Integer promptVersion,
    String guardrail) {

  /** A Gemini call. The row model and the billed model are the same. */
  public static WriterResult model(
      String text,
      String model,
      Integer inputTokens,
      Integer outputTokens,
      String promptName,
      Integer promptVersion) {
    return new WriterResult(text, model, model, inputTokens, outputTokens, promptName, promptVersion, "none");
  }

  /** The template. No tokens, and no prompt version, because no model ran. */
  public static WriterResult local(String text) {
    return new WriterResult(text, "local", null, null, null, null, null, "none");
  }

  /** Keep this text, and add the tokens from an earlier rejected call. */
  public WriterResult plusTokens(WriterResult earlier) {
    return new WriterResult(
        text,
        model,
        billedModel,
        add(inputTokens, earlier.inputTokens),
        add(outputTokens, earlier.outputTokens),
        promptName != null ? promptName : earlier.promptName,
        promptVersion != null ? promptVersion : earlier.promptVersion,
        guardrail);
  }

  /**
   * The local text replaced a model answer that failed the number check.
   * The span still names the model that was called, so Langfuse can price it.
   */
  public WriterResult keepingSpendFrom(WriterResult spent) {
    return new WriterResult(
        text,
        model,
        spent.billedModel(),
        spent.inputTokens(),
        spent.outputTokens(),
        spent.promptName(),
        spent.promptVersion(),
        "numbers");
  }

  private static Integer add(Integer left, Integer right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return left + right;
  }
}
