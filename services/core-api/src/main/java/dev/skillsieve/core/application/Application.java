package dev.skillsieve.core.application;

import dev.skillsieve.core.job.JobPost;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Tracking state for one job post (one-to-one). */
@Entity
@Table(name = "application")
public class Application {

  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_post_id", nullable = false, unique = true)
  private JobPost jobPost;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Nullable
  @Column(name = "applied_at")
  private Instant appliedAt;

  @Nullable
  @Column(name = "next_action_at")
  private LocalDate nextActionAt;

  @Nullable private String notes;

  @Column(nullable = false)
  private int position;

  @Version
  @Column(nullable = false)
  private int version;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Application() {}

  public Application(JobPost jobPost, Instant now) {
    this.id = UUID.randomUUID();
    this.jobPost = jobPost;
    this.status = Status.SAVED;
    this.updatedAt = now;
  }

  /** Changes status; returns true when it actually changed. Sets appliedAt on first apply. */
  boolean moveTo(Status next, Instant now) {
    if (next == status) {
      return false;
    }
    if (next == Status.APPLIED && appliedAt == null) {
      appliedAt = now;
    }
    status = next;
    updatedAt = now;
    return true;
  }

  void setNextActionAt(@Nullable LocalDate date, Instant now) {
    nextActionAt = date;
    updatedAt = now;
  }

  void setNotes(@Nullable String text, Instant now) {
    notes = text == null || text.isBlank() ? null : text;
    updatedAt = now;
  }

  void setPosition(int position, Instant now) {
    this.position = position;
    updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public JobPost getJobPost() {
    return jobPost;
  }

  public Status getStatus() {
    return status;
  }

  @Nullable
  public Instant getAppliedAt() {
    return appliedAt;
  }

  @Nullable
  public LocalDate getNextActionAt() {
    return nextActionAt;
  }

  @Nullable
  public String getNotes() {
    return notes;
  }

  public int getVersion() {
    return version;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
