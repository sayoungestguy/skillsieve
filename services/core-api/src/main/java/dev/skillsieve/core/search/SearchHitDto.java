package dev.skillsieve.core.search;

import dev.skillsieve.core.application.Status;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Snippet is split so clients never render server HTML. */
public record SearchHitDto(
    UUID jobId, @Nullable String company, @Nullable String title, Status status, Snippet snippet) {

  /** Plain text around the first match. */
  public record Snippet(String before, String match, String after) {}
}
