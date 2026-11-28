package dev.skillsieve.core.resume;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Resume tailoring settings, bound from {@code skillsieve.tailor.*}.
 *
 * @param timeout how long one skills-svc /v1/tailor call may take (CPU model, up to 2 attempts)
 * @param expireAfter a PENDING row older than this is shown as failed (e.g. pod restarted)
 */
@ConfigurationProperties("skillsieve.tailor")
public record TailorProperties(
    @DefaultValue("5m") Duration timeout, @DefaultValue("10m") Duration expireAfter) {}
