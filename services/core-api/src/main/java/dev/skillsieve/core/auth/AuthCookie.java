package dev.skillsieve.core.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** The HttpOnly cookie that carries the JWT (ARCHITECTURE §7). */
public final class AuthCookie {

  public static final String NAME = "ss_token";
  private static final String PATH = "/api";

  private AuthCookie() {}

  static ResponseCookie issue(String token, Duration ttl, boolean secure) {
    return base(token, secure).maxAge(ttl).build();
  }

  static ResponseCookie clear(boolean secure) {
    return base("", secure).maxAge(Duration.ZERO).build();
  }

  private static ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path(PATH);
  }
}
