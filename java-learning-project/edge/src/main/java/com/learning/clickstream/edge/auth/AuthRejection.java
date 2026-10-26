package com.learning.clickstream.edge.auth;

import lombok.Getter;

/**
 * An auth step said no. The HTTP layer maps status and code into a JSON body.
 * The exception message is the code only, so a stack trace cannot leak a key
 * or a token. Callers log the code and the path, not this stack.
 */
@Getter
public final class AuthRejection extends RuntimeException {
  private final int status;
  private final String code;

  public AuthRejection(int status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}
