package dev.skillsieve.core.job;

import dev.skillsieve.core.application.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Board card (GET /api/jobs). */
public record JobSummaryDto(
    UUID id,
    @Nullable String company,
    @Nullable String title,
    ExtractionStatus extractionStatus,
    List<String> topSkills,
    UUID applicationId,
    Status status,
    int version,
    @Nullable LocalDate nextActionAt,
    Instant updatedAt) {

  public JobSummaryDto {
    topSkills = List.copyOf(topSkills);
  }
}
