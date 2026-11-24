package dev.skillsieve.core.job;

import java.util.UUID;

/** The same (normalised) job text was already saved by this user. */
public class DuplicateJobException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final UUID existingId;

  public DuplicateJobException(UUID existingId) {
    super("Duplicate job post");
    this.existingId = existingId;
  }

  public UUID existingId() {
    return existingId;
  }
}
