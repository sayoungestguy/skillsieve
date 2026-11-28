package dev.skillsieve.core.resume;

/** Tailoring cannot start yet (no resume, no skills, or tailoring off). Mapped to 409. */
public class TailoringConflictException extends RuntimeException {
  public TailoringConflictException(String message) {
    super(message);
  }
}
