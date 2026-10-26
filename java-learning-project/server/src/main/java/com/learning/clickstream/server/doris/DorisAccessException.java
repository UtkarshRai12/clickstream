package com.learning.clickstream.server.doris;

/**
 * A Doris call failed. The message includes the URL and the response body so a
 * stream-load rejection is visible without a debugger.
 */
public final class DorisAccessException extends RuntimeException {

  public DorisAccessException(String message) {
    super(message);
  }

  public DorisAccessException(String message, Throwable cause) {
    super(message, cause);
  }
}
