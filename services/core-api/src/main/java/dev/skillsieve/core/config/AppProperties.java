package dev.skillsieve.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * App settings, bound from {@code skillsieve.*} (env vars in TECHNICAL_DESIGN §8).
 *
 * @param jwtSecret base64, at least 32 bytes (JWT_SECRET)
 * @param allowRandomJwtSecret local dev only: generate a secret when none is set
 * @param tokenTtl JWT and cookie lifetime
 * @param cookieSecure send the auth cookie only over HTTPS
 * @param ownerEmail seed this owner on first start (OWNER_EMAIL)
 * @param ownerPasswordHash BCrypt hash for the seeded owner (OWNER_PASSWORD_HASH)
 * @param ownerPassword local dev only: plain password, hashed at startup
 * @param loginAttemptsPerMinute login rate limit per client IP
 * @param skillsSvcUrl base URL of skills-svc (SKILLS_SVC_URL); empty = extraction off
 * @param internalToken shared secret sent as X-Internal-Token (INTERNAL_TOKEN)
 * @param extractionTimeout how long a save waits for skills-svc (ARCHITECTURE §4.1)
 * @param extractionMode SYNC (R1: call skills-svc during the save) or ASYNC (R2: outbox → RabbitMQ)
 * @param esUrl Elasticsearch base URL (ES_URL); empty = search and insights use Postgres
 */
@ConfigurationProperties("skillsieve")
public record AppProperties(
    @DefaultValue("") String jwtSecret,
    @DefaultValue("false") boolean allowRandomJwtSecret,
    @DefaultValue("1h") Duration tokenTtl,
    @DefaultValue("true") boolean cookieSecure,
    @DefaultValue("") String ownerEmail,
    @DefaultValue("") String ownerPasswordHash,
    @DefaultValue("") String ownerPassword,
    @DefaultValue("5") int loginAttemptsPerMinute,
    @DefaultValue("") String skillsSvcUrl,
    @DefaultValue("") String internalToken,
    @DefaultValue("10s") Duration extractionTimeout,
    @DefaultValue("SYNC") ExtractionMode extractionMode,
    @DefaultValue("") String esUrl) {

  /** How a saved post reaches skills-svc (ADR-006). EXTRACTION_MODE env var. */
  public enum ExtractionMode {
    SYNC,
    ASYNC
  }

  public boolean asyncExtraction() {
    return extractionMode == ExtractionMode.ASYNC;
  }
}
