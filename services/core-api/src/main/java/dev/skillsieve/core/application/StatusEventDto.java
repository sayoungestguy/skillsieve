package dev.skillsieve.core.application;

import java.time.Instant;
import org.springframework.lang.Nullable;

public record StatusEventDto(@Nullable Status fromStatus, Status toStatus, Instant changedAt) {

  static StatusEventDto from(StatusEvent e) {
    return new StatusEventDto(e.getFromStatus(), e.getToStatus(), e.getChangedAt());
  }
}
