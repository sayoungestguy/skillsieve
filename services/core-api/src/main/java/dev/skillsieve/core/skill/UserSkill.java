package dev.skillsieve.core.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** My self-rated level (0–3) for one skill. */
@Entity
@Table(name = "user_skill")
public class UserSkill {

  @EmbeddedId private Key id;

  @Column(nullable = false)
  private short level;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserSkill() {}

  public UserSkill(UUID userId, int skillId, int level, Instant now) {
    this.id = new Key(userId, skillId);
    setLevel(level, now);
  }

  public final void setLevel(int level, Instant now) {
    this.level = (short) level;
    this.updatedAt = now;
  }

  public int getSkillId() {
    return id.skillId;
  }

  public int getLevel() {
    return level;
  }

  /** Composite key (user_id, skill_id). */
  @Embeddable
  public static class Key implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "skill_id")
    private int skillId;

    protected Key() {}

    public Key(UUID userId, int skillId) {
      this.userId = userId;
      this.skillId = skillId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && k.skillId == skillId && k.userId.equals(userId);
    }

    @Override
    public int hashCode() {
      return 31 * userId.hashCode() + skillId;
    }
  }
}
