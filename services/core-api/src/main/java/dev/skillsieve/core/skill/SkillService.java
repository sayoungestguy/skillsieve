package dev.skillsieve.core.skill;

import dev.skillsieve.core.common.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

  /** Taxonomy + how often each skill appears in my saved posts + my level. */
  private static final String LIST_SQL =
      """
      WITH mine AS (SELECT id FROM job_post WHERE user_id = :userId),
           total AS (SELECT count(*) AS n FROM mine)
      SELECT s.slug, s.name, s.category,
             CASE WHEN total.n = 0 THEN 0
                  ELSE round(100.0 * count(js.job_post_id) / total.n)::int END AS frequency_pct,
             coalesce(us.level, 0) AS my_level
      FROM skill s
      CROSS JOIN total
      LEFT JOIN job_skill js ON js.skill_id = s.id AND js.job_post_id IN (SELECT id FROM mine)
      LEFT JOIN user_skill us ON us.skill_id = s.id AND us.user_id = :userId
      WHERE (CAST(:q AS text) IS NULL OR lower(s.name) LIKE :q OR s.slug LIKE :q
             OR EXISTS (SELECT 1 FROM skill_alias a WHERE a.skill_id = s.id AND a.alias LIKE :q))
        AND (CAST(:category AS text) IS NULL OR s.category = :category)
      GROUP BY s.id, total.n, us.level
      ORDER BY frequency_pct DESC, s.name
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final SkillRepository skills;
  private final UserSkillRepository userSkills;
  private final Clock clock;

  public SkillService(
      NamedParameterJdbcTemplate jdbc,
      SkillRepository skills,
      UserSkillRepository userSkills,
      Clock clock) {
    this.jdbc = jdbc;
    this.skills = skills;
    this.userSkills = userSkills;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<SkillDto> list(UUID userId, @Nullable String q, @Nullable String category) {
    String like =
        q == null || q.isBlank()
            ? null
            : "%" + q.strip().toLowerCase(Locale.ROOT).replaceAll("[\\\\%_]", "") + "%";
    var params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("q", like)
            .addValue(
                "category",
                category == null || category.isBlank() ? null : category.toUpperCase(Locale.ROOT));
    return jdbc.query(
        LIST_SQL,
        params,
        (rs, i) ->
            new SkillDto(
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getInt("frequency_pct"),
                rs.getInt("my_level")));
  }

  @Transactional
  public UserSkillDto setLevel(UUID userId, String slug, int level) {
    Skill skill = skills.findBySlug(slug).orElseThrow(() -> new NotFoundException("Skill"));
    var key = new UserSkill.Key(userId, skill.getId());
    UserSkill row =
        userSkills
            .findById(key)
            .orElseGet(() -> new UserSkill(userId, skill.getId(), level, clock.instant()));
    row.setLevel(level, clock.instant());
    userSkills.save(row);
    return new UserSkillDto(slug, level);
  }
}
