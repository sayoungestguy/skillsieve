package dev.skillsieve.core.job;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

/** PATCH body: null = leave unchanged, blank = clear. */
public record UpdateJobRequest(
    @Nullable @Size(max = 200) String company,
    @Nullable @Size(max = 200) String title,
    @Nullable @Size(max = 200) String location,
    @Nullable
        @Size(max = 2048)
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Use a full http(s) link.")
        String sourceUrl) {}
