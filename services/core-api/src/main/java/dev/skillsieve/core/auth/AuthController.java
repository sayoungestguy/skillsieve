package dev.skillsieve.core.auth;

import dev.skillsieve.core.common.CurrentUser;
import dev.skillsieve.core.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

  private final AuthService authService;
  private final LoginRateLimiter rateLimiter;
  private final AppProperties props;

  public AuthController(
      AuthService authService, LoginRateLimiter rateLimiter, AppProperties props) {
    this.authService = authService;
    this.rateLimiter = rateLimiter;
    this.props = props;
  }

  @PostMapping("/auth/login")
  ResponseEntity<Void> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    rateLimiter.check(request.getRemoteAddr());
    String token = authService.login(body.email(), body.password());
    return ResponseEntity.noContent()
        .header(
            HttpHeaders.SET_COOKIE,
            AuthCookie.issue(token, props.tokenTtl(), props.cookieSecure()).toString())
        .build();
  }

  @PostMapping("/auth/logout")
  ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, AuthCookie.clear(props.cookieSecure()).toString())
        .build();
  }

  @GetMapping("/me")
  MeDto me(Authentication auth) {
    return authService.me(CurrentUser.id(auth));
  }
}
