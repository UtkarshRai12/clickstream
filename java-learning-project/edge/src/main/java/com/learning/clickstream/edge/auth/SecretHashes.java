package com.learning.clickstream.edge.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 hex for API key secrets. The CSV stores the hash. Requests send the plaintext. */
public final class SecretHashes {
  private SecretHashes() {}

  public static String sha256Hex(String secret) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(secret.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(Character.forDigit((value >> 4) & 0xF, 16));
        hex.append(Character.forDigit(value & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
