package dev.skillsieve.core.common;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the signed-in user's id from the JWT subject. */
public final class CurrentUser {

  private CurrentUser() {}

  public static UUID id(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return UUID.fromString(jwt.getSubject());
    }
    throw new IllegalStateException("No authenticated user");
  }
}
