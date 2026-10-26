package com.learning.clickstream.edge.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;

/**
 * Turns the X-Api-Key header into a loaded key.
 * Lombok writes the constructor and Guice calls it. The expanded form is a
 * constructor annotated with @Inject that assigns the repository field.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ApiKeyVerifier {
  private final ApiKeyRepository keys;

  public ApiKey verify(String presentedSecret) {
    if (presentedSecret == null || presentedSecret.isBlank()) {
      throw new AuthRejection(401, "missing_api_key");
    }
    return keys.find(presentedSecret)
        .orElseThrow(() -> new AuthRejection(401, "unknown_api_key"));
  }
}
