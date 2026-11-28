package dev.skillsieve.core.resume;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TailoringRepository extends JpaRepository<Tailoring, Tailoring.Key> {

  /** Fails every PENDING request: their in-process runs are gone (restart or shutdown). */
  @Modifying
  @Query(
      "update Tailoring t set t.status = dev.skillsieve.core.resume.Tailoring.Status.FAILED,"
          + " t.error = :error, t.finishedAt = :now"
          + " where t.status = dev.skillsieve.core.resume.Tailoring.Status.PENDING")
  int failAllPending(@Param("error") String error, @Param("now") Instant now);
}
