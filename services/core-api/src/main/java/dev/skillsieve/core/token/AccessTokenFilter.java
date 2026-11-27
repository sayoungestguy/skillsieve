package dev.skillsieve.core.token;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer ssv_…} (Chrome extension). A token may only save a job and ask who
 * it belongs to; anything else is 403. The principal is a {@link Jwt} built in memory (claim {@code
 * pat=true}) so {@code CurrentUser} and role checks work unchanged. Cookie sessions are untouched.
 */
public final class AccessTokenFilter extends OncePerRequestFilter {

  static final String CLAIM = "pat";
  private static final String BEARER = "Bearer ";

  private final AccessTokenService tokens;

  public AccessTokenFilter(AccessTokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER + AccessTokenService.PREFIX)) {
      chain.doFilter(req, res);
      return;
    }
    if (!allowed(req)) {
      write(res, 403, "Forbidden", "An extension token can only save jobs.");
      return;
    }
    String plain = header.substring(BEARER.length()).strip();
    Optional<AccessTokenService.Owner> owner = tokens.authenticate(plain);
    if (owner.isEmpty()) {
      write(res, 401, "Unauthorized", "This token is not valid. Create a new one in Settings.");
      return;
    }
    AccessTokenService.Owner o = owner.get();
    Jwt jwt =
        Jwt.withTokenValue("pat")
            .header("alg", "none")
            .subject(o.userId().toString())
            .claim("role", o.role())
            .claim(CLAIM, true)
            .issuedAt(Instant.now())
            .build();
    var auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + o.role())));
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      chain.doFilter(req, res);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /** The extension's whole API surface. */
  static boolean allowed(HttpServletRequest req) {
    String path = req.getRequestURI();
    return ("POST".equals(req.getMethod()) && "/api/jobs".equals(path))
        || ("GET".equals(req.getMethod()) && "/api/me".equals(path));
  }

  private static void write(HttpServletResponse res, int status, String title, String detail)
      throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.getWriter()
        .write(
            "{\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(title, status, detail));
  }
}
