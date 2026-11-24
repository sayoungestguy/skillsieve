package dev.skillsieve.core.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record ApplicationDto(
    UUID id,
    Status status,
    @Nullable Instant appliedAt,
    @Nullable LocalDate nextActionAt,
    @Nullable String notes,
    int version) {

  public static ApplicationDto from(Application a) {
    return new ApplicationDto(
        a.getId(),
        a.getStatus(),
        a.getAppliedAt(),
        a.getNextActionAt(),
        a.getNotes(),
        a.getVersion());
  }
}
