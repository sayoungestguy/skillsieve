package dev.skillsieve.core.search;

import dev.skillsieve.core.application.Status;
import dev.skillsieve.core.common.PageDto;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fallback search with Postgres ILIKE, used when Elasticsearch is off or unreachable. */
@Component
public class PostgresSearch {

  static final int BEFORE = 60;
  static final int AFTER = 80;
  static final int NO_MATCH_PREVIEW = 140;
  static final int MAX_SIZE = 50;

  private static final String WHERE =
      """
      FROM job_post j JOIN application a ON a.job_post_id = j.id
      WHERE j.user_id = :userId
        AND (lower(coalesce(j.company,'')) LIKE :q OR lower(coalesce(j.title,'')) LIKE :q
             OR lower(j.raw_text) LIKE :q)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresSearch(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  PageDto<SearchHitDto> search(UUID userId, String query, int page, int size) {
    String term = query.strip();
    int pageSize = Math.clamp(size, 1, MAX_SIZE);
    if (term.isEmpty()) {
      return new PageDto<>(List.of(), 0, 0, pageSize);
    }
    String needle = term.toLowerCase(Locale.ROOT);
    var params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("q", "%" + needle.replaceAll("[\\\\%_]", "") + "%")
            .addValue("limit", pageSize)
            .addValue("offset", (long) Math.max(page, 0) * pageSize);
    Long total = jdbc.queryForObject("SELECT count(*) " + WHERE, params, Long.class);
    List<SearchHitDto> hits =
        jdbc.query(
            "SELECT j.id, j.company, j.title, j.raw_text, a.status "
                + WHERE
                + " ORDER BY j.created_at DESC LIMIT :limit OFFSET :offset",
            params,
            (rs, i) ->
                new SearchHitDto(
                    rs.getObject("id", UUID.class),
                    rs.getString("company"),
                    rs.getString("title"),
                    Status.valueOf(rs.getString("status")),
                    snippet(rs.getString("raw_text"), needle)));
    return new PageDto<>(hits, total == null ? 0 : total, Math.max(page, 0), pageSize);
  }

  static SearchHitDto.Snippet snippet(String text, String needle) {
    int at = text.toLowerCase(Locale.ROOT).indexOf(needle);
    if (at < 0) {
      return new SearchHitDto.Snippet(
          text.substring(0, Math.min(text.length(), NO_MATCH_PREVIEW)), "", "");
    }
    int end = Math.min(text.length(), at + needle.length());
    return new SearchHitDto.Snippet(
        text.substring(Math.max(0, at - BEFORE), at),
        text.substring(at, end),
        text.substring(end, Math.min(text.length(), end + AFTER)));
  }
}
