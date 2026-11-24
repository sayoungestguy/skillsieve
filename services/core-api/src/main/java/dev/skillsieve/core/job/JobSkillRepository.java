package dev.skillsieve.core.job;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSkillRepository extends JpaRepository<JobSkill, JobSkill.Key> {

  List<JobSkill> findByIdJobPostIdIn(Collection<java.util.UUID> jobPostIds);
}
