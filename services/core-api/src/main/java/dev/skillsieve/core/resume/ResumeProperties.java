package dev.skillsieve.core.resume;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Resume storage settings, bound from {@code skillsieve.resume.*}.
 *
 * @param key base64 of 32 random bytes (RESUME_KEY); empty = resume storage off
 */
@ConfigurationProperties("skillsieve.resume")
public record ResumeProperties(@DefaultValue("") String key) {}
