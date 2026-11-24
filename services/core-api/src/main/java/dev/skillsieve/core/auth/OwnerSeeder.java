package dev.skillsieve.core.auth;

import dev.skillsieve.core.config.AppProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the single R1 owner account on first start (OWNER_EMAIL + OWNER_PASSWORD_HASH). */
@Component
public class OwnerSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(OwnerSeeder.class);

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AppProperties props;
  private final Clock clock;

  public OwnerSeeder(
      AppUserRepository users, PasswordEncoder passwordEncoder, AppProperties props, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.props = props;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String email = props.ownerEmail().strip();
    if (email.isEmpty() || users.findByEmailIgnoreCase(email).isPresent()) {
      return;
    }
    String hash = props.ownerPasswordHash();
    if (hash.isBlank() && !props.ownerPassword().isBlank()) {
      hash = passwordEncoder.encode(props.ownerPassword());
    }
    if (hash.isBlank()) {
      log.warn("auth.seed skipped: OWNER_EMAIL is set but OWNER_PASSWORD_HASH is empty");
      return;
    }
    AppUser owner = users.save(new AppUser(email, hash, Role.OWNER, clock.instant()));
    log.info("auth.seed owner created userId={}", owner.getId());
  }
}
