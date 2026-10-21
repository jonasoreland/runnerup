package org.runnerup.importer;

/** A safe, user-displayable failure while reading an external activity document. */
public final class ActivityImportException extends Exception {
  public enum Reason {
    UNSUPPORTED_FORMAT,
    MALFORMED_XML,
    INPUT_TOO_LARGE,
    TOO_MANY_POINTS,
    MULTIPLE_ACTIVITIES,
    MISSING_ROUTE,
    INVALID_DATA
  }

  private final Reason reason;

  public ActivityImportException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public ActivityImportException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
