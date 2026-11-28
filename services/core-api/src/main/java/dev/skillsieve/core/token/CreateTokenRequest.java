package dev.skillsieve.core.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTokenRequest(@NotBlank @Size(max = 60) String name) {}
