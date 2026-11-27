package dev.skillsieve.core.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the exchange and queues on first connect (idempotent). Messages that skills-svc rejects
 * for good go to {@code skills.extract.dlq} through the dead-letter exchange.
 */
@Configuration
@ConditionalOnProperty(name = "skillsieve.extraction-mode", havingValue = "async")
public class RabbitTopology {

  @Bean
  Declarables extractionTopology() {
    TopicExchange events = new TopicExchange(Messaging.EXCHANGE, true, false);
    DirectExchange dlx = new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE, true, false);
    Queue extract =
        QueueBuilder.durable(Messaging.EXTRACT_QUEUE)
            .quorum()
            .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(Messaging.EXTRACT_QUEUE)
            .build();
    Queue dlq = QueueBuilder.durable(Messaging.EXTRACT_DLQ).build();
    Binding toExtract = BindingBuilder.bind(extract).to(events).with(Messaging.JOB_CREATED);
    Binding toDlq = BindingBuilder.bind(dlq).to(dlx).with(Messaging.EXTRACT_QUEUE);
    Queue results =
        QueueBuilder.durable(Messaging.RESULTS_QUEUE)
            .quorum()
            .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(Messaging.RESULTS_QUEUE)
            .build();
    Queue resultsDlq = QueueBuilder.durable(Messaging.RESULTS_DLQ).build();
    Binding toResults = BindingBuilder.bind(results).to(events).with(Messaging.SKILLS_RESULTS);
    Binding toResultsDlq = BindingBuilder.bind(resultsDlq).to(dlx).with(Messaging.RESULTS_QUEUE);
    return new Declarables(
        events, dlx, extract, dlq, results, resultsDlq, toExtract, toDlq, toResults, toResultsDlq);
  }
}
