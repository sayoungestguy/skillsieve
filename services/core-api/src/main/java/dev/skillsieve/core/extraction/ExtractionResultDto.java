package dev.skillsieve.core.extraction;

import dev.skillsieve.core.job.Importance;
import java.util.List;
import org.springframework.lang.Nullable;

/** skills-svc response (TECHNICAL_DESIGN §6). Unknown fields are ignored. */
public record ExtractionResultDto(
    @Nullable String company,
    @Nullable String title,
    @Nullable String location,
    @Nullable String seniority,
    @Nullable Integer minYears,
    List<Skill> skills,
    List<String> unmapped,
    String model,
    boolean cached) {

  public ExtractionResultDto {
    skills = skills == null ? List.of() : List.copyOf(skills);
    unmapped = unmapped == null ? List.of() : List.copyOf(unmapped);
  }

  /** One canonical skill found in the post. */
  public record Skill(String slug, Importance importance, @Nullable String evidence) {}
}
