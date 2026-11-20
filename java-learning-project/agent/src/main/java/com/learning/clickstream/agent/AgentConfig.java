package com.learning.clickstream.agent;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Non-secret settings for the agent process. Keys (Gemini, Langfuse) are not here.
 * They are read from the environment only, so they never sit in a committed file.
 * The trigger numbers are not here either. They are constants in the edge.
 */
public record AgentConfig(
    int httpPort,
    Path dbPath,
    String modelBaseUrl,
    String modelName,
    Duration modelTimeout,
    // Cloud project for the Gemini platform. Empty means the AI Studio key path.
    String gcpProject,
    // Platform location. "global" for current Gemini models. Ignored when gcpProject is empty.
    String gcpLocation) {}
