package dev.skillsieve.core.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "job_post")
public class JobPost {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Nullable private String company;
  @Nullable private String title;
  @Nullable private String location;

  @Nullable
  @Column(name = "source_url")
  private String sourceUrl;

  @Column(name = "raw_text", nullable = false)
  private String rawText;

  @Column(name = "text_hash", nullable = false, length = 64)
  private String textHash;

  @Nullable private String seniority;

  @Nullable
  @Column(name = "min_years")
  private Short minYears;

  @Enumerated(EnumType.STRING)
  @Column(name = "extraction_status", nullable = false)
  private ExtractionStatus extractionStatus;

  @Nullable
  @Column(name = "extraction_error")
  private String extractionError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected JobPost() {}

  public JobPost(
      UUID userId, String rawText, String textHash, @Nullable String sourceUrl, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.rawText = rawText;
    this.textHash = textHash;
    this.sourceUrl = sourceUrl;
    this.extractionStatus = ExtractionStatus.PENDING;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Applies the non-null fields of a PATCH. */
  void edit(UpdateJobRequest req, Instant now) {
    if (req.company() != null) {
      company = blankToNull(req.company());
    }
    if (req.title() != null) {
      title = blankToNull(req.title());
    }
    if (req.location() != null) {
      location = blankToNull(req.location());
    }
    if (req.sourceUrl() != null) {
      sourceUrl = blankToNull(req.sourceUrl());
    }
    updatedAt = now;
  }

  void requestExtraction(Instant now) {
    extractionStatus = ExtractionStatus.PENDING;
    extractionError = null;
    updatedAt = now;
  }

  @Nullable
  private static String blankToNull(String s) {
    return s.isBlank() ? null : s.strip();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  @Nullable
  public String getCompany() {
    return company;
  }

  @Nullable
  public String getTitle() {
    return title;
  }

  @Nullable
  public String getLocation() {
    return location;
  }

  @Nullable
  public String getSourceUrl() {
    return sourceUrl;
  }

  public String getRawText() {
    return rawText;
  }

  @Nullable
  public String getSeniority() {
    return seniority;
  }

  @Nullable
  public Short getMinYears() {
    return minYears;
  }

  public ExtractionStatus getExtractionStatus() {
    return extractionStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
