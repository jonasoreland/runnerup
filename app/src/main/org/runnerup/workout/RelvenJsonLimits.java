package org.runnerup.workout;

import java.nio.charset.StandardCharsets;

/** Bounds JSON input before org.json builds an object tree. */
final class RelvenJsonLimits {
  private static final int MAX_BYTES = 4_194_304;
  private static final int MAX_DEPTH = 32;
  private static final int MAX_TOKENS = 100_000;

  private RelvenJsonLimits() {}

  static void validate(String json) {
    if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new IllegalArgumentException("Invalid Relven JSON: package too large");
    }
    int depth = 0;
    int tokens = 0;
    boolean inString = false;
    boolean escaped = false;
    char previousSignificant = 0;
    for (int i = 0; i < json.length(); i++) {
      char current = json.charAt(i);
      if (inString) {
        if (escaped) escaped = false;
        else if (current == '\\') escaped = true;
        else if (current == '"') inString = false;
        continue;
      }
      if (current == '"') {
        inString = true;
        if (++tokens > MAX_TOKENS) throw tooManyTokens();
        previousSignificant = current;
      } else if (current == '{' || current == '[') {
        if (++depth > MAX_DEPTH) throw tooDeep();
        if (++tokens > MAX_TOKENS) throw tooManyTokens();
        previousSignificant = current;
      } else if (current == '}' || current == ']') {
        depth--;
        previousSignificant = current;
      } else if (current == ',' || current == ':') {
        previousSignificant = current;
      } else if (!Character.isWhitespace(current)) {
        if (previousSignificant == ':'
            || previousSignificant == '['
            || previousSignificant == ',') {
          if (++tokens > MAX_TOKENS) throw tooManyTokens();
        }
        previousSignificant = current;
      }
    }
  }

  static boolean isBlank(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isWhitespace(value.charAt(i))) return false;
    }
    return true;
  }

  private static IllegalArgumentException tooDeep() {
    return new IllegalArgumentException("Invalid Relven JSON: JSON nesting too deep");
  }

  private static IllegalArgumentException tooManyTokens() {
    return new IllegalArgumentException("Invalid Relven JSON: too many JSON tokens");
  }
}
