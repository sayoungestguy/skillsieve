package dev.skillsieve.core.skill;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetLevelRequest(@NotNull @Min(0) @Max(3) Integer level) {}
