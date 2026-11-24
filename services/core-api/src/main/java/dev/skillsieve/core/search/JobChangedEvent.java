package dev.skillsieve.core.search;

import java.util.UUID;

/**
 * A job, its status or its skills changed (deleted = true when the job is gone). Published inside
 * the DB transaction; the search index is updated after commit (TECHNICAL_DESIGN §4 sync rule).
 */
public record JobChangedEvent(UUID jobId, boolean deleted) {

  public static JobChangedEvent changed(UUID jobId) {
    return new JobChangedEvent(jobId, false);
  }

  public static JobChangedEvent deleted(UUID jobId) {
    return new JobChangedEvent(jobId, true);
  }
}
