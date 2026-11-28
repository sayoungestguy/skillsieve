package dev.skillsieve.core.resume;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Resume tailoring settings, bound from {@code skillsieve.tailor.*}.
 *
 * @param timeout how long one skills-svc /v1/tailor call may take: skills-svc may ask the model
 *     twice, up to 5 min each (TAILOR_TIMEOUT_S), so a little over 10 min
 * @param expireAfter a PENDING row older than this is shown as failed (e.g. pod restarted); longer
 *     than {@code timeout}
 */
@ConfigurationProperties("skillsieve.tailor")
public record TailorProperties(
    @DefaultValue("11m") Duration timeout, @DefaultValue("15m") Duration expireAfter) {}
