package dev.skillsieve.core.messaging;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drains the outbox to RabbitMQ every 2 s. A row is marked sent only after the broker confirms it,
 * so delivery is at-least-once; consumers dedupe by {@code eventId}. If the broker is down the rows
 * wait and the next run tries again.
 */
@Component
@ConditionalOnProperty(name = "skillsieve.extraction-mode", havingValue = "async")
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  static final int BATCH = 50;
  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

  private final OutboxRepository outbox;
  private final RabbitTemplate rabbit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public OutboxPublisher(
      OutboxRepository outbox, RabbitTemplate rabbit, TransactionTemplate tx, Clock clock) {
    this.outbox = outbox;
    this.rabbit = rabbit;
    this.tx = tx;
    this.clock = clock;
  }

  @Scheduled(initialDelay = 1_000, fixedDelay = 2_000)
  public void publishPending() {
    Integer sent = tx.execute(status -> drain());
    if (sent != null && sent > 0) {
      log.info("outbox.published count={}", sent);
    }
  }

  private int drain() {
    List<OutboxRepository.Row> rows = outbox.lockUnsent(BATCH);
    int sent = 0;
    for (OutboxRepository.Row row : rows) {
      try {
        send(row);
        outbox.markSent(row.id(), clock.instant());
        sent++;
      } catch (AmqpException e) {
        log.warn("outbox.publish_failed eventId={} reason={}", row.id(), e.getMessage());
        outbox.markFailed(row.id(), String.valueOf(e.getMessage()));
        break; // broker is likely down: keep order, try the rest next run
      }
    }
    return sent;
  }

  private void send(OutboxRepository.Row row) {
    rabbit.invoke(
        ops -> {
          ops.convertAndSend(
              Messaging.EXCHANGE,
              row.eventType(),
              row.payload(),
              m -> {
                m.getMessageProperties().setContentType("application/json");
                m.getMessageProperties().setMessageId(row.id().toString());
                m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return m;
              });
          ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
          return null;
        });
  }
}
