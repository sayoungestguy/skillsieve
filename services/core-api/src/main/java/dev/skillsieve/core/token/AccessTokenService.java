package dev.skillsieve.core.token;

import dev.skillsieve.core.auth.AppUser;
import dev.skillsieve.core.auth.AppUserRepository;
import dev.skillsieve.core.common.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal access tokens: {@code ssv_} + 32 random bytes (base64url). 256 bits of entropy, so a
 * plain SHA-256 is enough to store them (no slow hash needed; same idea as GitHub tokens).
 */
@Service
public class AccessTokenService {

  private static final Logger log = LoggerFactory.getLogger(AccessTokenService.class);
  public static final String PREFIX = "ssv_";
  static final int MAX_ACTIVE = 10;
  private static final int RANDOM_BYTES = 32;
  private static final int SHOWN_CHARS = 8;
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Who a valid token belongs to. */
  public record Owner(UUID userId, String email, String role) {}

  private final AccessTokenRepository tokens;
  private final AppUserRepository users;
  private final Clock clock;

  public AccessTokenService(AccessTokenRepository tokens, AppUserRepository users, Clock clock) {
    this.tokens = tokens;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public CreatedTokenDto create(UUID userId, String name) {
    if (tokens.countByUserIdAndRevokedAtIsNull(userId) >= MAX_ACTIVE) {
      throw new TooManyTokensException();
    }
    byte[] bytes = new byte[RANDOM_BYTES];
    RANDOM.nextBytes(bytes);
    String plain = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    AccessToken token =
        tokens.save(
            new AccessToken(
                userId,
                name.strip(),
                sha256(plain),
                plain.substring(0, PREFIX.length() + SHOWN_CHARS),
                clock.instant()));
    log.info("token.created tokenId={} userId={}", token.getId(), userId);
    return new CreatedTokenDto(AccessTokenDto.of(token), plain);
  }

  @Transactional(readOnly = true)
  public List<AccessTokenDto> list(UUID userId) {
    return tokens.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId).stream()
        .map(AccessTokenDto::of)
        .toList();
  }

  @Transactional
  public void revoke(UUID userId, UUID tokenId) {
    AccessToken token =
        tokens.findByIdAndUserId(tokenId, userId).orElseThrow(() -> new NotFoundException("Token"));
    token.revoke(clock.instant());
    log.info("token.revoked tokenId={}", tokenId);
  }

  /** The owner of an active token, or empty. Records the use. */
  @Transactional
  public Optional<Owner> authenticate(String plain) {
    if (!plain.startsWith(PREFIX)) {
      return Optional.empty();
    }
    return tokens
        .findByTokenHash(sha256(plain))
        .filter(AccessToken::isActive)
        .flatMap(
            t -> {
              Optional<AppUser> user = users.findById(t.getUserId());
              user.ifPresent(u -> t.touch(clock.instant()));
              return user.map(u -> new Owner(u.getId(), u.getEmail(), u.getRole().name()));
            });
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 missing", e);
    }
  }
}
