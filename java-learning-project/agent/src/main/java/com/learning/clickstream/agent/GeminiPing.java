package com.learning.clickstream.agent;

import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One live call to the Gemini platform, using the same client the agent uses.
 * Run from java-learning-project:
 *
 *   mvn -pl agent exec:java -Dexec.mainClass=com.learning.clickstream.agent.GeminiPing
 *
 * Project and location come from config/agent.properties. Auth is the
 * Application Default Credentials already on this machine.
 */
public final class GeminiPing {
  private GeminiPing() {}

  public static void main(String[] args) {
    // exec:java runs with the module directory as cwd, so the file is one level up.
    Path file = AgentConfigLoader.DEFAULT_FILE;
    if (!Files.exists(file)) {
      file = Path.of("..").resolve(AgentConfigLoader.DEFAULT_FILE);
    }
    AgentConfig config = AgentConfigLoader.load(file, System.getenv());
    if (config.gcpProject() == null || config.gcpProject().isBlank()) {
      throw new IllegalStateException("agent.gcp.project is empty");
    }
    ChatModel model = IncidentWriters.platformModel(
        config.gcpProject(), config.gcpLocation(), config.modelName(), 1);
    // A fixed prompt so a successful call is obvious in the terminal.
    String reply = model.chat("Reply with the single word pong");
    System.out.println("model=" + config.modelName()
        + " project=" + config.gcpProject()
        + " location=" + config.gcpLocation());
    System.out.println(reply);
  }
}
