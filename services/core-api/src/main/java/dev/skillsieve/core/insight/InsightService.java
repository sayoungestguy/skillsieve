package dev.skillsieve.core.insight;

import dev.skillsieve.core.application.Status;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Board stats and the skill-gap table, computed in Postgres. Elasticsearch takes over the skill
 * aggregation in Days 6–8 (TECHNICAL_DESIGN §4); the response shape stays the same.
 */
@Service
public class InsightService {

  /** Gap rule (DESIGN §3.4): in at least 30% of posts and my level is 0 or 1. */
  static final int GAP_MIN_PCT = 30;

  static final int GAP_MAX_LEVEL = 1;
  static final int MAX_LIMIT = 100;

  private static final String SUMMARY_SQL =
      """
      SELECT count(*) AS total,
             count(*) FILTER (WHERE a.status <> 'SAVED') AS applied,
             count(*) FILTER (WHERE a.status IN ('INTERVIEW','OFFER')) AS interviews,
             count(*) FILTER (WHERE a.status = 'OFFER') AS offers
      FROM job_post j JOIN application a ON a.job_post_id = j.id
      WHERE j.user_id = :userId
      """;

  private static final String SKILLS_SQL =
      """
      WITH scope AS (
        SELECT j.id FROM job_post j JOIN application a ON a.job_post_id = j.id
        WHERE j.user_id = :userId
          AND (CAST(:status AS text) IS NULL OR a.status = :status)
          AND (CAST(:from AS timestamptz) IS NULL OR j.created_at >= :from)
          AND (CAST(:to AS timestamptz) IS NULL OR j.created_at < :to)
      ), total AS (SELECT greatest(count(*), 1) AS n FROM scope)
      SELECT s.slug, s.name, s.category,
             round(100.0 * count(*) FILTER (WHERE js.importance = 'REQUIRED') / total.n)::int AS req,
             round(100.0 * count(*) FILTER (WHERE js.importance = 'PREFERRED') / total.n)::int AS pref,
             round(100.0 * count(*) / total.n)::int AS tot,
             coalesce(us.level, 0) AS my_level
      FROM job_skill js
      JOIN scope ON scope.id = js.job_post_id
      JOIN skill s ON s.id = js.skill_id
      CROSS JOIN total
      LEFT JOIN user_skill us ON us.skill_id = s.id AND us.user_id = :userId
      GROUP BY s.id, total.n, us.level
      ORDER BY tot DESC, s.name
      LIMIT :limit
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public InsightService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public InsightSummaryDto summary(UUID userId) {
    return jdbc.queryForObject(
        SUMMARY_SQL,
        new MapSqlParameterSource("userId", userId),
        (rs, i) -> {
          long applied = rs.getLong("applied");
          long interviews = rs.getLong("interviews");
          return new InsightSummaryDto(
              rs.getLong("total"),
              applied,
              interviews,
              rs.getLong("offers"),
              applied == 0 ? 0 : (double) interviews / applied);
        });
  }

  @Transactional(readOnly = true)
  public List<SkillInsightDto> skills(
      UUID userId,
      @Nullable Status status,
      @Nullable Instant from,
      @Nullable Instant to,
      int limit) {
    var params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("status", status == null ? null : status.name())
            .addValue("from", from == null ? null : Timestamp.from(from))
            .addValue("to", to == null ? null : Timestamp.from(to))
            .addValue("limit", Math.clamp(limit, 1, MAX_LIMIT));
    return jdbc.query(
        SKILLS_SQL,
        params,
        (rs, i) -> {
          int total = rs.getInt("tot");
          int level = rs.getInt("my_level");
          return new SkillInsightDto(
              rs.getString("slug"),
              rs.getString("name"),
              rs.getString("category"),
              rs.getInt("req"),
              rs.getInt("pref"),
              total,
              level,
              isGap(total, level));
        });
  }

  static boolean isGap(int totalPct, int level) {
    return totalPct >= GAP_MIN_PCT && level <= GAP_MAX_LEVEL;
  }
}
