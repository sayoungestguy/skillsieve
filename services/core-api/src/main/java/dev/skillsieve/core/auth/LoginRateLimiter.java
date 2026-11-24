package dev.skillsieve.core.auth;

import dev.skillsieve.core.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Sliding-window limit on login attempts per client IP. In memory: one replica in R1. */
@Component
public final class LoginRateLimiter {

  private static final Duration WINDOW = Duration.ofMinutes(1);
  private static final int MAX_TRACKED_CLIENTS = 10_000;

  private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
  private final int limit;
  private final Clock clock;

  public LoginRateLimiter(AppProperties props, Clock clock) {
    this.limit = props.loginAttemptsPerMinute();
    this.clock = clock;
  }

  /** Records an attempt; throws when the client is over the limit. */
  public void check(String clientIp) {
    if (attempts.size() > MAX_TRACKED_CLIENTS) {
      attempts.clear();
    }
    Instant now = clock.instant();
    Deque<Instant> window = attempts.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
    synchronized (window) {
      while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(WINDOW))) {
        window.pollFirst();
      }
      if (window.size() >= limit) {
        throw new TooManyLoginAttemptsException();
      }
      window.addLast(now);
    }
  }
}
