package dev.skillsieve.core.job;

import dev.skillsieve.core.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** A skill extracted from a job post. Written by skills-svc flow (Days 6–8); read here. */
@Entity
@Table(name = "job_skill")
public class JobSkill {

  @EmbeddedId private Key id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "skill_id", insertable = false, updatable = false)
  private Skill skill;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Importance importance;

  protected JobSkill() {}

  public UUID getJobPostId() {
    return id.jobPostId;
  }

  public Skill getSkill() {
    return skill;
  }

  public Importance getImportance() {
    return importance;
  }

  /** Composite key (job_post_id, skill_id). */
  @Embeddable
  public static class Key implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "job_post_id")
    private UUID jobPostId;

    @Column(name = "skill_id")
    private int skillId;

    protected Key() {}

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && k.skillId == skillId && k.jobPostId.equals(jobPostId);
    }

    @Override
    public int hashCode() {
      return 31 * jobPostId.hashCode() + skillId;
    }
  }
}
