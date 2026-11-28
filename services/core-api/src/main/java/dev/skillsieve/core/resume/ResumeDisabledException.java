package dev.skillsieve.core.resume;

/** RESUME_KEY is not set, so resumes cannot be stored. Mapped to 503. */
public class ResumeDisabledException extends RuntimeException {
  public ResumeDisabledException() {
    super("Resume storage is not configured (RESUME_KEY)");
  }
}
