package dev.skillsieve.core.token;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

  Optional<AccessToken> findByTokenHash(String tokenHash);

  List<AccessToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

  Optional<AccessToken> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndRevokedAtIsNull(UUID userId);
}
