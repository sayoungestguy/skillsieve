package dev.skillsieve.core.skill;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSkillRepository extends JpaRepository<UserSkill, UserSkill.Key> {

  List<UserSkill> findByIdUserId(UUID userId);
}
