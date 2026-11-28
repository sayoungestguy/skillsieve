package dev.skillsieve.core.resume;

import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Suggestions for one job. {@code stale} = the resume or the job post changed since they were made,
 * so the web app offers to regenerate.
 */
public record TailoringDto(
    Tailoring.Status status,
    boolean stale,
    @Nullable String model,
    List<TailorResultDto.Suggestion> suggestions,
    @Nullable String error,
    Instant requestedAt,
    @Nullable Instant finishedAt) {}
