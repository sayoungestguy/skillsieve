package dev.skillsieve.core.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Plain JDBC over {@code outbox}: append in the caller's transaction, drain with SKIP LOCKED. */
@Repository
public class OutboxRepository {

  /** One unsent event as stored. {@code payload} is the JSON message body. */
  public record Row(UUID id, String eventType, String payload) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public OutboxRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /**
   * Adds a {@code job.created} event. Must run inside the transaction that saved the job. The text
   * itself is not sent: skills-svc reads it from the database by id (TECHNICAL_DESIGN §10).
   */
  public UUID appendJobCreated(UUID jobId, String textHash, Instant now) {
    UUID eventId = UUID.randomUUID();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("eventId", eventId.toString());
    body.put("type", Messaging.JOB_CREATED);
    body.put("jobId", jobId.toString());
    body.put("textHash", textHash);
    body.put("occurredAt", now.toString());
    String payload;
    try {
      payload = json.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot serialise outbox event", e);
    }
    jdbc.update(
        "INSERT INTO outbox (id, event_type, aggregate_id, payload, created_at)"
            + " VALUES (?, ?, ?, ?::jsonb, ?)",
        eventId,
        Messaging.JOB_CREATED,
        jobId,
        payload,
        Timestamp.from(now));
    return eventId;
  }

  /** Oldest unsent rows, locked for this transaction; other replicas skip them. */
  public List<Row> lockUnsent(int limit) {
    return jdbc.query(
        "SELECT id, event_type, payload::text FROM outbox WHERE sent_at IS NULL"
            + " ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED",
        (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
        limit);
  }

  public void markSent(UUID id, Instant now) {
    jdbc.update(
        "UPDATE outbox SET sent_at = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?",
        Timestamp.from(now),
        id);
  }

  public void markFailed(UUID id, String error) {
    jdbc.update(
        "UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?", error, id);
  }

  /** Unsent count, for logs and tests. */
  public long unsentCount() {
    Long n = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE sent_at IS NULL", Long.class);
    return n == null ? 0 : n;
  }
}
