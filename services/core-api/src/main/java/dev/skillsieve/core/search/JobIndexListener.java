package dev.skillsieve.core.search;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Applies {@link JobChangedEvent}s to the search index after the DB commit. */
@Component
public class JobIndexListener {

  private final JobIndex index;

  public JobIndexListener(JobIndex index) {
    this.index = index;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(JobChangedEvent event) {
    index.sync(event.jobId(), event.deleted());
  }
}
