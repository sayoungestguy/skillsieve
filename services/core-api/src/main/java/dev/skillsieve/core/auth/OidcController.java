package dev.skillsieve.core.auth;

import dev.skillsieve.core.config.AppProperties;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keycloak sign-in endpoints (ADR-009). Browser navigations, not XHR, so they are GETs that answer
 * with redirects. Any failure lands on /login?oidc=&lt;reason&gt; for the web app to explain.
 */
@RestController
@RequestMapping("/api/auth")
public class OidcController {

  private static final Logger log = LoggerFactory.getLogger(OidcController.class);

  /** Which sign-in methods the login page should show. */
  public record AuthConfigDto(boolean oidcEnabled, @Nullable String oidcLogoutUrl) {}

  private final OidcService oidc;
  private final OidcFlowCookie flowCookie;
  private final AppProperties app;
  private final java.time.Clock clock;

  public OidcController(
      OidcService oidc,
      javax.crypto.SecretKey jwtKey,
      com.fasterxml.jackson.databind.ObjectMapper json,
      AppProperties app,
      java.time.Clock clock) {
    this.oidc = oidc;
    this.flowCookie = new OidcFlowCookie(jwtKey, json);
    this.app = app;
    this.clock = clock;
  }

  @GetMapping("/config")
  AuthConfigDto config() {
    return oidc.enabled()
        ? new AuthConfigDto(true, oidc.logoutUrl())
        : new AuthConfigDto(false, null);
  }

  @GetMapping("/oidc/login")
  ResponseEntity<Void> login() {
    if (!oidc.enabled()) {
      return ResponseEntity.notFound().build();
    }
    OidcService.Start start = oidc.start();
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(start.authorizeUrl()))
        .header(
            HttpHeaders.SET_COOKIE, flowCookie.issue(start.flow(), app.cookieSecure()).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .build();
  }

  @GetMapping("/oidc/callback")
  ResponseEntity<Void> callback(
      @RequestParam(required = false) @Nullable String code,
      @RequestParam(required = false) @Nullable String state,
      @RequestParam(required = false) @Nullable String error,
      @CookieValue(name = OidcFlowCookie.NAME, required = false) @Nullable String cookie) {
    if (!oidc.enabled()) {
      return ResponseEntity.notFound().build();
    }
    if (error != null) {
      return toLogin("cancelled");
    }
    Optional<OidcFlowCookie.Flow> flow =
        Optional.ofNullable(cookie).flatMap(c -> flowCookie.read(c, clock.instant()));
    if (flow.isEmpty() || state == null || !state.equals(flow.get().state()) || code == null) {
      log.warn("oidc.callback_rejected reason=state");
      return toLogin("expired");
    }
    try {
      String session = oidc.finish(code, flow.get());
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create("/"))
          .header(
              HttpHeaders.SET_COOKIE,
              AuthCookie.issue(session, app.tokenTtl(), app.cookieSecure()).toString())
          .header(HttpHeaders.SET_COOKIE, OidcFlowCookie.clear(app.cookieSecure()).toString())
          .build();
    } catch (OidcLoginException e) {
      log.warn("oidc.callback_failed reason={} detail={}", e.reason(), e.getMessage());
      return toLogin(e.reason());
    }
  }

  private ResponseEntity<Void> toLogin(String reason) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("/login?oidc=" + reason))
        .header(HttpHeaders.SET_COOKIE, OidcFlowCookie.clear(app.cookieSecure()).toString())
        .build();
  }
}
