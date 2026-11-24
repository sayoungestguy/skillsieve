package dev.skillsieve.core.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads search documents straight from Postgres (the source of truth). */
@Component
public class JobDocuments {

  private static final String SELECT =
      """
      SELECT j.id, j.user_id, j.company, j.title, j.raw_text, j.seniority, j.created_at, a.status,
             coalesce(array_agg(s.slug ORDER BY s.slug) FILTER (WHERE js.importance = 'REQUIRED'),
                      '{}') AS required,
             coalesce(array_agg(s.slug ORDER BY s.slug) FILTER (WHERE js.importance = 'PREFERRED'),
                      '{}') AS preferred
      FROM job_post j
      JOIN application a ON a.job_post_id = j.id
      LEFT JOIN job_skill js ON js.job_post_id = j.id
      LEFT JOIN skill s ON s.id = js.skill_id
      """;
  private static final String GROUP = " GROUP BY j.id, a.status";

  private final NamedParameterJdbcTemplate jdbc;

  public JobDocuments(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<JobDocument> find(UUID jobId) {
    List<JobDocument> rows =
        jdbc.query(
            SELECT + " WHERE j.id = :id" + GROUP,
            new MapSqlParameterSource("id", jobId),
            (rs, i) -> map(rs));
    return rows.stream().findFirst();
  }

  /** Streams every job (for a full reindex) without loading them all at once. */
  public void forEach(Consumer<JobDocument> sink) {
    jdbc.query(
        SELECT + GROUP,
        rs -> {
          sink.accept(map(rs));
        });
  }

  private static JobDocument map(ResultSet rs) throws SQLException {
    List<String> required = slugs(rs, "required");
    List<String> preferred = slugs(rs, "preferred");
    List<String> all = new ArrayList<>(required);
    all.addAll(preferred);
    return new JobDocument(
        rs.getString("id"),
        rs.getString("user_id"),
        rs.getString("company"),
        rs.getString("title"),
        rs.getString("raw_text"),
        rs.getString("status"),
        rs.getString("seniority"),
        new JobDocument.Skills(required, preferred, all),
        rs.getTimestamp("created_at").toInstant());
  }

  private static List<String> slugs(ResultSet rs, String column) throws SQLException {
    var array = rs.getArray(column);
    return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
  }
}
