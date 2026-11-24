package dev.skillsieve.core.job;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobSkillRepository extends JpaRepository<JobSkill, JobSkill.Key> {

  List<JobSkill> findByIdJobPostIdIn(Collection<java.util.UUID> jobPostIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from JobSkill js where js.id.jobPostId = :jobPostId")
  void deleteForJob(@Param("jobPostId") java.util.UUID jobPostId);
}
