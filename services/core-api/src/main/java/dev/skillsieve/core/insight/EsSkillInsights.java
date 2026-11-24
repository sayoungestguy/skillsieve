package dev.skillsieve.core.insight;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import dev.skillsieve.core.application.Status;
import dev.skillsieve.core.search.JobIndex;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;

/** Skill-gap aggregation on Elasticsearch (TECHNICAL_DESIGN §4 gap query). */
final class EsSkillInsights {

  /** Enough buckets to cover the whole taxonomy. */
  static final int BUCKETS = 500;

  private static final String SKILL_SQL =
      """
      SELECT s.slug, s.name, s.category, coalesce(us.level, 0) AS my_level
      FROM skill s LEFT JOIN user_skill us ON us.skill_id = s.id AND us.user_id = :userId
      WHERE s.slug IN (:slugs)
      """;

  private EsSkillInsights() {}

  private record SkillInfo(String name, String category, int level) {}

  static List<SkillInsightDto> query(
      ElasticsearchClient client,
      NamedParameterJdbcTemplate jdbc,
      UUID userId,
      @Nullable Status status,
      @Nullable Instant from,
      @Nullable Instant to,
      int limit)
      throws IOException {
    List<Query> filters = new ArrayList<>();
    filters.add(Query.of(q -> q.term(t -> t.field("userId").value(userId.toString()))));
    if (status != null) {
      filters.add(Query.of(q -> q.term(t -> t.field("status").value(status.name()))));
    }
    if (from != null || to != null) {
      filters.add(
          Query.of(
              q ->
                  q.range(
                      r ->
                          r.untyped(
                              u -> {
                                u.field("createdAt");
                                if (from != null) {
                                  u.gte(JsonData.of(from.toString()));
                                }
                                if (to != null) {
                                  u.lt(JsonData.of(to.toString()));
                                }
                                return u;
                              }))));
    }
    SearchResponse<Void> res =
        client.search(
            s ->
                s.index(JobIndex.ALIAS)
                    .size(0)
                    .trackTotalHits(t -> t.enabled(true))
                    .query(q -> q.bool(b -> b.filter(filters)))
                    .aggregations(
                        "required", a -> a.terms(t -> t.field("skills.required").size(BUCKETS)))
                    .aggregations(
                        "preferred", a -> a.terms(t -> t.field("skills.preferred").size(BUCKETS)))
                    .aggregations("all", a -> a.terms(t -> t.field("skills.all").size(BUCKETS))),
            Void.class);

    long total = Math.max(res.hits().total() == null ? 0 : res.hits().total().value(), 1);
    Map<String, Long> required = counts(res, "required");
    Map<String, Long> preferred = counts(res, "preferred");
    Map<String, Long> all = counts(res, "all");
    if (all.isEmpty()) {
      return List.of();
    }
    Map<String, SkillInfo> info = skillInfo(jdbc, userId, all.keySet());

    return all.entrySet().stream()
        .filter(e -> info.containsKey(e.getKey()))
        .map(
            e -> {
              SkillInfo s = info.get(e.getKey());
              int totalPct = pct(e.getValue(), total);
              return new SkillInsightDto(
                  e.getKey(),
                  s.name(),
                  s.category(),
                  pct(required.getOrDefault(e.getKey(), 0L), total),
                  pct(preferred.getOrDefault(e.getKey(), 0L), total),
                  totalPct,
                  s.level(),
                  InsightService.isGap(totalPct, s.level()));
            })
        .sorted(
            Comparator.comparingInt(SkillInsightDto::totalPct)
                .reversed()
                .thenComparing(SkillInsightDto::name))
        .limit(Math.clamp(limit, 1, InsightService.MAX_LIMIT))
        .toList();
  }

  private static Map<String, Long> counts(SearchResponse<Void> res, String name) {
    return res.aggregations().get(name).sterms().buckets().array().stream()
        .collect(
            Collectors.toMap(b -> b.key().stringValue(), StringTermsBucket::docCount, Long::sum));
  }

  private static Map<String, SkillInfo> skillInfo(
      NamedParameterJdbcTemplate jdbc, UUID userId, java.util.Set<String> slugs) {
    var params = new MapSqlParameterSource("userId", userId).addValue("slugs", slugs);
    return jdbc
        .query(
            SKILL_SQL,
            params,
            (rs, i) ->
                Map.entry(
                    rs.getString("slug"),
                    new SkillInfo(
                        rs.getString("name"), rs.getString("category"), rs.getInt("my_level"))))
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static int pct(long count, long total) {
    return (int) Math.round(100.0 * count / total);
  }
}
