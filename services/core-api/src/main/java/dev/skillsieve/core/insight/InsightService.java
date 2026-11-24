package dev.skillsieve.core.insight;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.skillsieve.core.application.Status;
import dev.skillsieve.core.search.JobIndex;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Board stats (Postgres) and the skill-gap table: Elasticsearch aggregations when available
 * (TECHNICAL_DESIGN §4), otherwise the same numbers from Postgres.
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

  private static final Logger log = LoggerFactory.getLogger(InsightService.class);

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectProvider<ElasticsearchClient> es;
  private final JobIndex index;

  public InsightService(
      NamedParameterJdbcTemplate jdbc, ObjectProvider<ElasticsearchClient> es, JobIndex index) {
    this.jdbc = jdbc;
    this.es = es;
    this.index = index;
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

  public List<SkillInsightDto> skills(
      UUID userId,
      @Nullable Status status,
      @Nullable Instant from,
      @Nullable Instant to,
      int limit) {
    ElasticsearchClient client = es.getIfAvailable();
    if (client != null && index.ready()) {
      try {
        return EsSkillInsights.query(client, jdbc, userId, status, from, to, limit);
      } catch (IOException | RuntimeException e) {
        log.warn("insights.es failed, using Postgres: {}", e.getMessage());
      }
    }
    return skillsFromPostgres(userId, status, from, to, limit);
  }

  @Transactional(readOnly = true)
  List<SkillInsightDto> skillsFromPostgres(
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
