package dev.skillsieve.core.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only taxonomy row, seeded from data/taxonomy/skills.yaml. */
@Entity
@Table(name = "skill")
public class Skill {

  @Id private Integer id;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String category;

  protected Skill() {}

  public Integer getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public String getCategory() {
    return category;
  }
}
