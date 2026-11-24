package dev.skillsieve.core.job;

import dev.skillsieve.core.application.Application;
import dev.skillsieve.core.application.ApplicationDto;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Entity → DTO mapping (hand-written, CODING_STANDARDS §3). */
final class JobMapper {

  static final int TOP_SKILLS = 3;

  private JobMapper() {}

  static JobDto toDto(Application app, List<JobSkill> skills, Map<Integer, Integer> myLevels) {
    JobPost j = app.getJobPost();
    List<JobDto.JobSkillDto> skillDtos =
        sorted(skills).stream()
            .map(
                s ->
                    new JobDto.JobSkillDto(
                        s.getSkill().getSlug(),
                        s.getSkill().getName(),
                        s.getImportance(),
                        myLevels.getOrDefault(s.getSkill().getId(), 0)))
            .toList();
    return new JobDto(
        j.getId(),
        j.getCompany(),
        j.getTitle(),
        j.getLocation(),
        j.getSourceUrl(),
        j.getRawText(),
        j.getSeniority(),
        j.getMinYears(),
        j.getExtractionStatus(),
        j.getExtractionError(),
        skillDtos,
        ApplicationDto.from(app),
        j.getCreatedAt());
  }

  static JobSummaryDto toSummary(Application app, List<JobSkill> skills) {
    JobPost j = app.getJobPost();
    List<String> top =
        sorted(skills).stream()
            .filter(s -> s.getImportance() == Importance.REQUIRED)
            .limit(TOP_SKILLS)
            .map(s -> s.getSkill().getName())
            .toList();
    Instant updated =
        app.getUpdatedAt().isAfter(j.getUpdatedAt()) ? app.getUpdatedAt() : j.getUpdatedAt();
    return new JobSummaryDto(
        j.getId(),
        j.getCompany(),
        j.getTitle(),
        j.getExtractionStatus(),
        top,
        app.getId(),
        app.getStatus(),
        app.getVersion(),
        app.getNextActionAt(),
        updated);
  }

  /** Required first, then by name, so output is stable. */
  private static List<JobSkill> sorted(List<JobSkill> skills) {
    return skills.stream()
        .sorted(
            Comparator.comparing(JobSkill::getImportance)
                .thenComparing(s -> s.getSkill().getName()))
        .toList();
  }
}
