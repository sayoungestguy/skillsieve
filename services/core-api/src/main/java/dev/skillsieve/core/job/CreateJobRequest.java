package dev.skillsieve.core.job;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateJobRequest(
    @NotNull @Size(min = 200, max = 30_000, message = "Paste between 200 and 30,000 characters.")
        String rawText,
    @Nullable
        @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "Use a full http(s) link.")
        String sourceUrl) {}
