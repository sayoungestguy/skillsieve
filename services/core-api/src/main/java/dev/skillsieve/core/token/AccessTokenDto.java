package dev.skillsieve.core.token;

import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** A token as listed in settings: never the secret, only its prefix. */
public record AccessTokenDto(
    UUID id, String name, String prefix, Instant createdAt, @Nullable Instant lastUsedAt) {

  static AccessTokenDto of(AccessToken t) {
    return new AccessTokenDto(
        t.getId(), t.getName(), t.getPrefix(), t.getCreatedAt(), t.getLastUsedAt());
  }
}
