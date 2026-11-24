package dev.skillsieve.core.auth;

import dev.skillsieve.core.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String ISSUER = "skillsieve-core-api";

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final AppProperties props;
  private final Clock clock;

  public AuthService(
      AppUserRepository users,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      AppProperties props,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.props = props;
    this.clock = clock;
  }

  /** Returns a signed JWT for valid credentials. */
  @Transactional(readOnly = true)
  public String login(String email, String password) {
    AppUser user =
        users
            .findByEmailIgnoreCase(email.strip())
            .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
            .orElseThrow(InvalidCredentialsException::new);
    log.info("auth.login userId={}", user.getId());
    return tokenFor(user);
  }

  @Transactional(readOnly = true)
  public MeDto me(UUID userId) {
    AppUser user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
    return new MeDto(user.getId(), user.getEmail(), user.getRole());
  }

  String tokenFor(AppUser user) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(props.tokenTtl()))
            .claim("role", user.getRole().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
