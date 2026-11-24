package dev.skillsieve.core.search;

import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/** Document in index alias job-posts-current (mapping: resources/es/job-posts-mapping.json). */
public record JobDocument(
    String jobId,
    String userId,
    @Nullable String company,
    @Nullable String title,
    String body,
    String status,
    @Nullable String seniority,
    Skills skills,
    Instant createdAt) {

  /** Skill slugs by importance; {@code all} = both. */
  public record Skills(List<String> required, List<String> preferred, List<String> all) {

    public Skills {
      required = List.copyOf(required);
      preferred = List.copyOf(preferred);
      all = List.copyOf(all);
    }
  }
}
