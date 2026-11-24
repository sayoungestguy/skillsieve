package dev.skillsieve.core.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.skillsieve.core.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

  @Test
  void shouldBlockSixthAttemptWithinAMinute() {
    var props = new AppProperties("", false, Duration.ofHours(1), true, "", "", "", 5);
    var limiter =
        new LoginRateLimiter(
            props, Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC));
    for (int i = 0; i < 5; i++) {
      limiter.check("10.0.0.1");
    }

    assertThatThrownBy(() -> limiter.check("10.0.0.1"))
        .isInstanceOf(TooManyLoginAttemptsException.class);
    assertThatCode(() -> limiter.check("10.0.0.2")).doesNotThrowAnyException();
  }
}
