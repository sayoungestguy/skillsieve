package dev.skillsieve.core.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Append-only history of status changes. */
@Entity
@Table(name = "status_event")
public class StatusEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Nullable
  @Enumerated(EnumType.STRING)
  @Column(name = "from_status")
  private Status fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false)
  private Status toStatus;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected StatusEvent() {}

  public StatusEvent(UUID applicationId, @Nullable Status from, Status to, Instant at) {
    this.applicationId = applicationId;
    this.fromStatus = from;
    this.toStatus = to;
    this.changedAt = at;
  }

  @Nullable
  public Status getFromStatus() {
    return fromStatus;
  }

  public Status getToStatus() {
    return toStatus;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
