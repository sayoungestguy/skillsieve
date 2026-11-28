package dev.skillsieve.core.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseCookie;

/**
 * Short-lived, HMAC-signed cookie holding the OIDC state, nonce and PKCE verifier between the
 * redirect to Keycloak and the callback. SameSite=Lax because the callback is a top-level
 * navigation coming back from the identity provider; HttpOnly and scoped to the OIDC paths.
 */
final class OidcFlowCookie {

  static final String NAME = "ss_oidc";
  static final Duration TTL = Duration.ofMinutes(10);
  private static final String PATH = "/api/auth/oidc";
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  /** What the callback needs to finish the flow. */
  record Flow(String state, String nonce, String verifier, long expiresAt) {}

  private final SecretKeySpec key;
  private final ObjectMapper json;

  OidcFlowCookie(SecretKey jwtKey, ObjectMapper json) {
    // Separate key derived from the JWT secret, so a flow cookie can never pass as a session.
    this.key =
        new SecretKeySpec(
            hmac(
                new SecretKeySpec(jwtKey.getEncoded(), "HmacSHA256"),
                "oidc-flow-cookie".getBytes(StandardCharsets.UTF_8)),
            "HmacSHA256");
    this.json = json;
  }

  ResponseCookie issue(Flow flow, boolean secure) {
    try {
      String payload = B64.encodeToString(json.writeValueAsBytes(flow));
      String sig = B64.encodeToString(hmac(key, payload.getBytes(StandardCharsets.US_ASCII)));
      return base(payload + "." + sig, secure).maxAge(TTL).build();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  static ResponseCookie clear(boolean secure) {
    return base("", secure).maxAge(Duration.ZERO).build();
  }

  /** The flow if the cookie is present, untampered and not expired. */
  Optional<Flow> read(String value, Instant now) {
    int dot = value.indexOf('.');
    if (dot <= 0) {
      return Optional.empty();
    }
    String payload = value.substring(0, dot);
    byte[] expected = hmac(key, payload.getBytes(StandardCharsets.US_ASCII));
    byte[] given;
    try {
      given = B64D.decode(value.substring(dot + 1));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    if (!MessageDigest.isEqual(expected, given)) {
      return Optional.empty();
    }
    try {
      Flow flow = json.readValue(B64D.decode(payload), Flow.class);
      return flow.expiresAt() > now.getEpochSecond() ? Optional.of(flow) : Optional.empty();
    } catch (java.io.IOException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path(PATH);
  }

  private static byte[] hmac(SecretKeySpec k, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(k);
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
