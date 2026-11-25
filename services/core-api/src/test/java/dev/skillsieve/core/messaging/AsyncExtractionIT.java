package dev.skillsieve.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.skillsieve.core.IntegrationTest;
import dev.skillsieve.core.auth.Role;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * R2 async path (ARCHITECTURE §4.2) with a real broker. skills-svc is played by the test: it reads
 * job.created from skills.extract and publishes job.skills.* results back.
 */
@TestPropertySource(properties = "skillsieve.extraction-mode=async")
class AsyncExtractionIT extends IntegrationTest {

  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-alpine");

  static {
    RABBIT.start();
  }

  @DynamicPropertySource
  static void rabbit(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
  }

  private static final String RESULT =
      """
      {"company":"Northwind Labs","title":"Backend Engineer","location":"Singapore",
       "seniority":"MID","minYears":3,
       "skills":[{"slug":"java","raw":"Java","importance":"REQUIRED","evidence":null},
                 {"slug":"kubernetes","raw":"K8s","importance":"PREFERRED","evidence":null}],
       "unmapped":[],"model":"gemini-test","promptVersion":"extract-v1","cached":false}
      """;

  @Autowired RabbitTemplate rabbit;
  @Autowired OutboxRepository outbox;

  private Cookie user;

  @BeforeEach
  void signIn() throws Exception {
    user = newUser(Role.USER);
    while (rabbit.receive(Messaging.EXTRACT_QUEUE) != null) {
      // drain events left by other tests
    }
  }

  private JsonNode nextEvent() throws Exception {
    Message m = rabbit.receive(Messaging.EXTRACT_QUEUE, 10_000);
    assertThat(m).as("job.created on %s", Messaging.EXTRACT_QUEUE).isNotNull();
    return json.readTree(new String(m.getBody(), StandardCharsets.UTF_8));
  }

  private JsonNode createJob(String marker) throws Exception {
    return body(
        mvc.perform(
                ajax(post("/api/jobs"), user)
                    .content(
                        json.writeValueAsString(
                            Map.of("rawText", jobText(marker + UUID.randomUUID())))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  /** Plays skills-svc: publish a job.skills.* event for a job.created event. */
  private void reply(JsonNode created, UUID eventId, String type, String textHash, String fields) {
    String body =
        "{\"eventId\":\"%s\",\"type\":\"%s\",\"causationId\":\"%s\",\"jobId\":\"%s\",\"textHash\":\"%s\",%s}"
            .formatted(
                eventId,
                type,
                created.get("eventId").asText(),
                created.get("jobId").asText(),
                textHash,
                fields);
    rabbit.convertAndSend(Messaging.EXCHANGE, type, body);
  }

  private JsonNode waitUntil(String id, Predicate<JsonNode> done) throws Exception {
    JsonNode job = null;
    for (int i = 0; i < 100; i++) {
      job = body(mvc.perform(get("/api/jobs/" + id).cookie(user)).andReturn());
      if (done.test(job)) {
        return job;
      }
      Thread.sleep(100);
    }
    return job;
  }

  @Test
  void shouldQueueExtractionInsteadOfCallingSkillsSvc() throws Exception {
    JsonNode job = createJob("Async-");

    assertThat(job.get("extractionStatus").asText()).isEqualTo("PENDING");
    assertThat(job.get("extractionError").isNull()).as("skills-svc was not called").isTrue();

    JsonNode event = nextEvent();
    assertThat(event.get("type").asText()).isEqualTo("job.created");
    assertThat(event.get("jobId").asText()).isEqualTo(job.get("id").asText());
    assertThat(event.get("textHash").asText()).hasSize(64);
    assertThat(UUID.fromString(event.get("eventId").asText())).isNotNull();
    assertThat(event.has("text")).as("text stays in the database").isFalse();
    assertThat(outbox.unsentCount()).isZero();
  }

  @Test
  void shouldQueueAgainWhenUserAsksForRetry() throws Exception {
    String id = createJob("Retry-").get("id").asText();
    String first = nextEvent().get("eventId").asText();

    mvc.perform(ajax(post("/api/jobs/" + id + "/extract"), user)).andExpect(status().isAccepted());

    JsonNode again = nextEvent();
    assertThat(again.get("jobId").asText()).isEqualTo(id);
    assertThat(again.get("eventId").asText()).isNotEqualTo(first);
  }

  @Test
  void shouldStoreSkillsFromResultEventOnce() throws Exception {
    String id = createJob("Result-").get("id").asText();
    JsonNode created = nextEvent();
    UUID eventId = UUID.randomUUID();

    reply(
        created,
        eventId,
        "job.skills.extracted",
        created.get("textHash").asText(),
        "\"result\":" + RESULT);
    JsonNode done = waitUntil(id, j -> j.get("extractionStatus").asText().equals("DONE"));

    assertThat(done.get("extractionStatus").asText()).isEqualTo("DONE");
    assertThat(done.get("company").asText()).isEqualTo("Northwind Labs");
    assertThat(done.get("skills")).hasSize(2);

    // Redelivery of the same event (at-least-once) changes nothing, even with other content.
    reply(
        created,
        eventId,
        "job.skills.failed",
        created.get("textHash").asText(),
        "\"retryLater\":false");
    Thread.sleep(500);
    JsonNode after = body(mvc.perform(get("/api/jobs/" + id).cookie(user)).andReturn());
    assertThat(after.get("extractionStatus").asText()).isEqualTo("DONE");
  }

  @Test
  void shouldShowTryLaterWhenSkillsSvcGivesUp() throws Exception {
    String id = createJob("GaveUp-").get("id").asText();
    JsonNode created = nextEvent();

    reply(
        created,
        UUID.randomUUID(),
        "job.skills.failed",
        created.get("textHash").asText(),
        "\"retryLater\":true,\"reason\":\"LlmQuotaError\"");
    JsonNode job = waitUntil(id, j -> !j.get("extractionError").isNull());

    assertThat(job.get("extractionStatus").asText()).isEqualTo("PENDING");
    assertThat(job.get("extractionError").asText()).contains("Try again later");
  }

  @Test
  void shouldIgnoreResultForOtherText() throws Exception {
    String id = createJob("Stale-").get("id").asText();
    JsonNode created = nextEvent();

    reply(
        created, UUID.randomUUID(), "job.skills.extracted", "0".repeat(64), "\"result\":" + RESULT);
    Thread.sleep(500);

    JsonNode job = body(mvc.perform(get("/api/jobs/" + id).cookie(user)).andReturn());
    assertThat(job.get("extractionStatus").asText()).isEqualTo("PENDING");
    assertThat(job.get("skills")).isEmpty();
  }

  @Test
  void shouldDeadLetterUnreadableResult() {
    rabbit.convertAndSend(Messaging.EXCHANGE, "job.skills.extracted", "not json");

    Message dead = rabbit.receive(Messaging.RESULTS_DLQ, 10_000);
    assertThat(dead).isNotNull();
    assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("not json");
  }
}
