package dev.skillsieve.core.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "access_token")
public class AccessToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String name;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(nullable = false)
  private String prefix;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Nullable
  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Nullable
  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected AccessToken() {}

  AccessToken(UUID userId, String name, String tokenHash, String prefix, Instant now) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.name = name;
    this.tokenHash = tokenHash;
    this.prefix = prefix;
    this.createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getPrefix() {
    return prefix;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Nullable
  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  void touch(Instant now) {
    lastUsedAt = now;
  }

  void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }
}
