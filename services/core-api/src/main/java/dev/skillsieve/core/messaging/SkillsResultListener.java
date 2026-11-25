package dev.skillsieve.core.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillsieve.core.extraction.ExtractionService;
import dev.skillsieve.core.job.JobPostRepository;
import java.sql.Timestamp;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies skills-svc results (ARCHITECTURE §4.2). One transaction per message: record the eventId
 * in {@code processed_event}, then store skills or the failure. A redelivered event is skipped. A
 * result for text the job no longer has is ignored. Unreadable messages go to the results DLQ.
 */
@Component
@ConditionalOnProperty(name = "skillsieve.extraction-mode", havingValue = "async")
public class SkillsResultListener {

  private static final Logger log = LoggerFactory.getLogger(SkillsResultListener.class);

  private final ObjectMapper json;
  private final ExtractionService extraction;
  private final JobPostRepository jobs;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Clock clock;

  public SkillsResultListener(
      ObjectMapper json,
      ExtractionService extraction,
      JobPostRepository jobs,
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      Clock clock) {
    this.json = json;
    this.extraction = extraction;
    this.jobs = jobs;
    this.jdbc = jdbc;
    this.tx = tx;
    this.clock = clock;
  }

  @RabbitListener(queues = Messaging.RESULTS_QUEUE, autoStartup = "true")
  public void onResult(Message message) {
    SkillsResultEvent event;
    try {
      event = json.readValue(message.getBody(), SkillsResultEvent.class);
    } catch (java.io.IOException e) {
      log.warn("results.bad_message size={}", message.getBody().length);
      throw new AmqpRejectAndDontRequeueException("unreadable result event", e);
    }
    tx.executeWithoutResult(status -> apply(event));
  }

  void apply(SkillsResultEvent event) {
    int inserted =
        jdbc.update(
            "INSERT INTO processed_event (event_id, processed_at) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING",
            event.eventId(),
            Timestamp.from(clock.instant()));
    if (inserted == 0) {
      log.info("results.duplicate eventId={}", event.eventId());
      return;
    }
    boolean current =
        jobs.findById(event.jobId())
            .map(job -> job.getTextHash().equals(event.textHash()))
            .orElse(false);
    if (!current) {
      log.info("results.stale_or_deleted jobId={}", event.jobId());
      return;
    }
    switch (event.type()) {
      case Messaging.SKILLS_EXTRACTED -> {
        if (event.result() == null) {
          throw new AmqpRejectAndDontRequeueException("job.skills.extracted without result");
        }
        extraction.apply(event.jobId(), event.result());
      }
      case Messaging.SKILLS_FAILED -> {
        log.warn("results.failed jobId={} reason={}", event.jobId(), event.reason());
        extraction.markFailed(event.jobId(), Boolean.TRUE.equals(event.retryLater()));
      }
      default -> log.warn("results.unknown_type type={}", event.type());
    }
  }
}
