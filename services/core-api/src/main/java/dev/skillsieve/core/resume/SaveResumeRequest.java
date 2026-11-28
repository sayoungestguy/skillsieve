package dev.skillsieve.core.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveResumeRequest(@NotBlank @Size(max = ResumeService.MAX_CHARS) String text) {}
