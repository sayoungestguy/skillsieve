package dev.skillsieve.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cheap checks before auth (TECHNICAL_DESIGN §10): state-changing API calls must carry {@code
 * X-Requested-With} (a cross-site form cannot set it), and bodies are capped at 64 KB.
 */
public final class ApiRequestGuardFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Requested-With";
  static final long MAX_BODY_BYTES = 64L * 1024;
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getRequestURI().startsWith("/api/")) {
      if (req.getContentLengthLong() > MAX_BODY_BYTES) {
        reject(res, 413, "Payload too large", "Request body is limited to 64 KB.");
        return;
      }
      if (!SAFE_METHODS.contains(req.getMethod()) && req.getHeader(HEADER) == null) {
        reject(res, 403, "Forbidden", "Missing X-Requested-With header.");
        return;
      }
    }
    chain.doFilter(req, res);
  }

  private static void reject(HttpServletResponse res, int status, String title, String detail)
      throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.getWriter()
        .write(
            "{\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(title, status, detail));
  }
}
