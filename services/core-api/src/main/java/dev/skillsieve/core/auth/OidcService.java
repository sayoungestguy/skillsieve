package dev.skillsieve.core.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Keycloak sign-in run by core-api as a confidential client (ADR-009): authorization code flow with
 * PKCE (S256), state and nonce. The ID token is checked (signature via JWKS, issuer, audience,
 * expiry, nonce), then mapped to an {@link AppUser}; the caller sets the usual cookie.
 */
@Service
public class OidcService {

  private static final Logger log = LoggerFactory.getLogger(OidcService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final int TIMEOUT_MS = 5_000;

  private final OidcProperties props;
  private final AppUserRepository users;
  private final AuthService auth;
  private final Clock clock;
  private final RestClient http;
  private volatile NimbusJwtDecoder decoder;

  public OidcService(
      OidcProperties props,
      AppUserRepository users,
      AuthService auth,
      Clock clock,
      RestClient.Builder builder) {
    this.props = props;
    this.users = users;
    this.auth = auth;
    this.clock = clock;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT_MS);
    factory.setReadTimeout(TIMEOUT_MS);
    this.http = builder.requestFactory(factory).build();
  }

  public boolean enabled() {
    return props.enabled();
  }

  /** A fresh flow and the Keycloak URL to send the browser to. */
  record Start(OidcFlowCookie.Flow flow, String authorizeUrl) {}

  Start start() {
    String state = random();
    String nonce = random();
    String verifier = random() + random();
    String challenge = B64.encodeToString(sha256(verifier));
    long expires = clock.instant().plus(OidcFlowCookie.TTL).getEpochSecond();
    String url =
        props.publicBase()
            + "/protocol/openid-connect/auth?"
            + query(
                Map.of(
                    "response_type",
                    "code",
                    "client_id",
                    props.clientId(),
                    "redirect_uri",
                    props.redirectUri(),
                    "scope",
                    "openid email profile",
                    "state",
                    state,
                    "nonce",
                    nonce,
                    "code_challenge",
                    challenge,
                    "code_challenge_method",
                    "S256"));
    return new Start(new OidcFlowCookie.Flow(state, nonce, verifier, expires), url);
  }

  /** Exchanges the code, validates the ID token and returns our session JWT. */
  String finish(String code, OidcFlowCookie.Flow flow) {
    Jwt idToken = validate(exchange(code, flow.verifier()), flow.nonce());
    AppUser user = resolve(idToken);
    return auth.tokenFor(user, true);
  }

  /** Keycloak's end-session URL: logs out of Keycloak, then back to our login page. */
  String logoutUrl() {
    return props.publicBase()
        + "/protocol/openid-connect/logout?"
        + query(
            Map.of("client_id", props.clientId(), "post_logout_redirect_uri", props.loginPage()));
  }

  private String exchange(String code, String verifier) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("redirect_uri", props.redirectUri());
    form.add("code_verifier", verifier);
    try {
      Map<?, ?> body =
          http.post()
              .uri(props.backchannelBase() + "/protocol/openid-connect/token")
              .headers(h -> h.setBasicAuth(enc(props.clientId()), enc(props.clientSecret())))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);
      Object idToken = body == null ? null : body.get("id_token");
      if (!(idToken instanceof String s) || s.isBlank()) {
        throw new OidcLoginException("error", "no id_token in token response");
      }
      return s;
    } catch (RestClientException e) {
      throw new OidcLoginException("error", "token exchange failed", e);
    }
  }

  private Jwt validate(String idToken, String nonce) {
    Jwt jwt;
    try {
      jwt = decoder().decode(idToken);
    } catch (JwtException e) {
      throw new OidcLoginException("error", "invalid id_token: " + e.getMessage(), e);
    }
    // Checked here, not in the shared decoder, because the nonce differs per sign-in.
    if (!MessageDigest.isEqual(
        nonce.getBytes(StandardCharsets.UTF_8),
        String.valueOf(jwt.getClaimAsString("nonce")).getBytes(StandardCharsets.UTF_8))) {
      throw new OidcLoginException("error", "invalid id_token: bad nonce");
    }
    return jwt;
  }

  /** Signature (JWKS, cached by Nimbus), issuer, expiry and audience; built once. */
  private NimbusJwtDecoder decoder() {
    NimbusJwtDecoder d = decoder;
    if (d == null) {
      d =
          NimbusJwtDecoder.withJwkSetUri(props.backchannelBase() + "/protocol/openid-connect/certs")
              .build();
      d.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefaultWithIssuer(props.publicBase()),
              claim(
                  "aud",
                  jwt ->
                      jwt.getAudience() != null && jwt.getAudience().contains(props.clientId()))));
      decoder = d;
    }
    return d;
  }

  /** Same Keycloak identity → same user; else link a verified email; else a new USER. */
  AppUser resolve(Jwt idToken) {
    String issuer = props.publicBase();
    String subject = idToken.getSubject();
    Optional<AppUser> known = users.findByOidcIssuerAndOidcSubject(issuer, subject);
    if (known.isPresent()) {
      log.info("oidc.login userId={}", known.get().getId());
      return known.get();
    }
    String email = idToken.getClaimAsString("email");
    if (email == null || email.isBlank()) {
      throw new OidcLoginException("no_email", "Keycloak account has no email");
    }
    Optional<AppUser> byEmail = users.findByEmailIgnoreCase(email.strip());
    if (byEmail.isPresent()) {
      // Linking to an existing account needs proof the person owns that email; otherwise anyone
      // could register "owner@…" in Keycloak and take over the owner's account.
      if (!Boolean.TRUE.equals(idToken.getClaimAsBoolean("email_verified"))) {
        throw new OidcLoginException("unverified", "email not verified for an existing account");
      }
      AppUser user = byEmail.get();
      user.linkOidc(issuer, subject);
      log.info("oidc.linked userId={}", user.getId());
      return users.save(user);
    }
    AppUser created =
        users.save(
            AppUser.fromOidc(
                email.strip().toLowerCase(java.util.Locale.ROOT),
                issuer,
                subject,
                clock.instant()));
    log.info("oidc.created userId={}", created.getId());
    return created;
  }

  private static OAuth2TokenValidator<Jwt> claim(
      String name, java.util.function.Predicate<Jwt> ok) {
    return jwt ->
        ok.test(jwt)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "bad " + name, null));
  }

  private static String random() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return B64.encodeToString(bytes);
  }

  private static byte[] sha256(String s) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** client_secret_basic requires form-encoding the id and secret first (RFC 6749 §2.3.1). */
  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static String query(Map<String, String> params) {
    return String.join(
        "&",
        params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
            .toList());
  }
}
