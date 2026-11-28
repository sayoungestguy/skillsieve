package dev.skillsieve.core.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "tailoring")
public class Tailoring {

  /** Where a request is. */
  public enum Status {
    PENDING,
    DONE,
    FAILED
  }

  @EmbeddedId private Key id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "resume_hmac", nullable = false)
  private String resumeHmac;

  @Column(name = "job_text_hash", nullable = false)
  private String jobTextHash;

  @Nullable private byte[] ciphertext;

  @Nullable private String error;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Nullable
  @Column(name = "finished_at")
  private Instant finishedAt;

  protected Tailoring() {}

  Tailoring(UUID userId, UUID jobPostId) {
    this.id = new Key(userId, jobPostId);
  }

  void start(String resumeHmac, String jobTextHash, Instant now) {
    this.status = Status.PENDING;
    this.resumeHmac = resumeHmac;
    this.jobTextHash = jobTextHash;
    this.ciphertext = null;
    this.error = null;
    this.requestedAt = now;
    this.finishedAt = null;
  }

  void succeed(byte[] ciphertext, Instant now) {
    this.status = Status.DONE;
    this.ciphertext = ciphertext.clone();
    this.finishedAt = now;
  }

  void fail(String error, Instant now) {
    this.status = Status.FAILED;
    this.error = error;
    this.finishedAt = now;
  }

  public UUID getUserId() {
    return id.userId;
  }

  public UUID getJobPostId() {
    return id.jobPostId;
  }

  public Status getStatus() {
    return status;
  }

  public String getResumeHmac() {
    return resumeHmac;
  }

  public String getJobTextHash() {
    return jobTextHash;
  }

  @Nullable
  byte[] getCiphertext() {
    return ciphertext == null ? null : ciphertext.clone();
  }

  @Nullable
  public String getError() {
    return error;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  @Nullable
  public Instant getFinishedAt() {
    return finishedAt;
  }

  @Embeddable
  public static class Key implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "job_post_id")
    private UUID jobPostId;

    protected Key() {}

    public Key(UUID userId, UUID jobPostId) {
      this.userId = userId;
      this.jobPostId = jobPostId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && userId.equals(k.userId) && jobPostId.equals(k.jobPostId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, jobPostId);
    }
  }
}
