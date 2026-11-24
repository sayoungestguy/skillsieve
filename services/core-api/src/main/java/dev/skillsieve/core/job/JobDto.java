package dev.skillsieve.core.job;

import dev.skillsieve.core.application.ApplicationDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record JobDto(
    UUID id,
    @Nullable String company,
    @Nullable String title,
    @Nullable String location,
    @Nullable String sourceUrl,
    String rawText,
    @Nullable String seniority,
    @Nullable Short minYears,
    ExtractionStatus extractionStatus,
    List<JobSkillDto> skills,
    ApplicationDto application,
    Instant createdAt) {

  public JobDto {
    skills = List.copyOf(skills);
  }

  /** A skill on a job, with my own level. */
  public record JobSkillDto(String slug, String name, Importance importance, int myLevel) {}
}
