package dev.skillsieve.core.job;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostRepository extends JpaRepository<JobPost, UUID> {

  Optional<JobPost> findByUserIdAndTextHash(UUID userId, String textHash);

  Optional<JobPost> findByIdAndUserId(UUID id, UUID userId);
}
