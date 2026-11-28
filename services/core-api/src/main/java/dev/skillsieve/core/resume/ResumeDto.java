package dev.skillsieve.core.resume;

import java.time.Instant;

/** My resume, decrypted for me only. */
public record ResumeDto(String text, int charCount, Instant updatedAt) {}
