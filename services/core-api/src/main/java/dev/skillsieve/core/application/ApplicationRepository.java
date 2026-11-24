package dev.skillsieve.core.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

  @Query(
      value =
          """
          select a from Application a join fetch a.jobPost j
          where j.userId = :userId
            and (:status is null or a.status = :status)
            and (:q is null or lower(j.company) like :q or lower(j.title) like :q)
          order by a.position, j.createdAt desc
          """,
      countQuery =
          """
          select count(a) from Application a join a.jobPost j
          where j.userId = :userId
            and (:status is null or a.status = :status)
            and (:q is null or lower(j.company) like :q or lower(j.title) like :q)
          """)
  Page<Application> findBoard(
      @Param("userId") UUID userId,
      @Param("status") @Nullable Status status,
      @Param("q") @Nullable String likePattern,
      Pageable pageable);

  @Query("select a from Application a join fetch a.jobPost j where j.id = :jobId")
  Optional<Application> findByJobPostId(@Param("jobId") UUID jobId);

  @Query(
      "select a from Application a join fetch a.jobPost j where a.id = :id and j.userId = :userId")
  Optional<Application> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
