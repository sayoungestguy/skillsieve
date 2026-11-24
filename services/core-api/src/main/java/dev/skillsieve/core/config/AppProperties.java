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
    @DefaultValue("10s") Duration extractionTimeout) {}
