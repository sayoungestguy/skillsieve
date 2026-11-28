package dev.skillsieve.core.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "app_user")
public class AppUser {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  /** Null for users who only sign in with Keycloak. */
  @Nullable
  @Column(name = "password_hash")
  private String passwordHash;

  @Nullable
  @Column(name = "oidc_issuer")
  private String oidcIssuer;

  @Nullable
  @Column(name = "oidc_subject")
  private String oidcSubject;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AppUser() {}

  public AppUser(String email, @Nullable String passwordHash, Role role, Instant now) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = now;
  }

  /** A new USER who signs in with Keycloak only. */
  static AppUser fromOidc(String email, String issuer, String subject, Instant now) {
    AppUser user = new AppUser(email, null, Role.USER, now);
    user.linkOidc(issuer, subject);
    return user;
  }

  void linkOidc(String issuer, String subject) {
    this.oidcIssuer = issuer;
    this.oidcSubject = subject;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  @Nullable
  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }
}
