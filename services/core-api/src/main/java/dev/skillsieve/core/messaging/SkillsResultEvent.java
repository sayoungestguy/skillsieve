package dev.skillsieve.core.messaging;

import dev.skillsieve.core.extraction.ExtractionResultDto;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * skills-svc → core-api on {@code core.skills-extracted} (TECHNICAL_DESIGN §14). {@code result} is
 * set for {@code job.skills.extracted}; {@code retryLater} for {@code job.skills.failed}.
 */
public record SkillsResultEvent(
    UUID eventId,
    String type,
    @Nullable UUID causationId,
    UUID jobId,
    String textHash,
    @Nullable ExtractionResultDto result,
    @Nullable Boolean retryLater,
    @Nullable String reason) {}
