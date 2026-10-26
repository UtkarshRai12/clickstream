package com.learning.clickstream.edge.auth;

/** Checks a compact JWT and returns the caller it names. */
public interface TokenVerifier {
  Caller verify(String compactJwt);
}
