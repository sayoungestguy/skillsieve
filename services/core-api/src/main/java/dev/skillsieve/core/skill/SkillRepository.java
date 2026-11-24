package dev.skillsieve.core.skill;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Integer> {

  Optional<Skill> findBySlug(String slug);

  List<Skill> findBySlugIn(Collection<String> slugs);
}
