package dev.skillsieve.core.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.skillsieve.core.application.Status;
import dev.skillsieve.core.common.PageDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Full-text search over my saved posts (TECHNICAL_DESIGN §4). Elasticsearch when available; if it
 * is off or fails, Postgres answers instead so search keeps working.
 */
@Service
public class SearchService {

  /** Control characters as highlight tags: never present in pasted text, easy to split on. */
  static final String PRE = "\u0002";

  static final String POST = "\u0003";
  static final int FRAGMENT_CHARS = 180;
  static final int MAX_SIZE = 50;
  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

  private final ObjectProvider<ElasticsearchClient> es;
  private final PostgresSearch fallback;
  private final ObjectProvider<JobIndex> index;

  public SearchService(
      ObjectProvider<ElasticsearchClient> es,
      PostgresSearch fallback,
      ObjectProvider<JobIndex> index) {
    this.es = es;
    this.fallback = fallback;
    this.index = index;
  }

  public PageDto<SearchHitDto> search(UUID userId, String query, int page, int size) {
    String term = query.strip();
    int pageSize = Math.clamp(size, 1, MAX_SIZE);
    ElasticsearchClient client = es.getIfAvailable();
    JobIndex jobIndex = index.getIfAvailable();
    if (term.isEmpty() || client == null || jobIndex == null || !jobIndex.ready()) {
      return fallback.search(userId, query, page, size);
    }
    try {
      return searchEs(client, userId, term, Math.max(page, 0), pageSize);
    } catch (IOException | RuntimeException e) {
      log.warn("search.es failed, using Postgres: {}", e.getMessage());
      return fallback.search(userId, query, page, size);
    }
  }

  @SuppressWarnings("unchecked")
  private static PageDto<SearchHitDto> searchEs(
      ElasticsearchClient client, UUID userId, String term, int page, int size) throws IOException {
    SearchResponse<Map> res =
        client.search(
            s ->
                s.index(JobIndex.ALIAS)
                    .from(page * size)
                    .size(size)
                    .trackTotalHits(t -> t.enabled(true))
                    .query(
                        q ->
                            q.bool(
                                b ->
                                    b.filter(
                                            f ->
                                                f.term(
                                                    t ->
                                                        t.field("userId").value(userId.toString())))
                                        .must(
                                            m ->
                                                m.multiMatch(
                                                    mm ->
                                                        mm.query(term)
                                                            .type(TextQueryType.BestFields)
                                                            .fields(
                                                                "company^3",
                                                                "title^3",
                                                                "body",
                                                                "skills.all^2")))))
                    .highlight(
                        h ->
                            h.preTags(PRE)
                                .postTags(POST)
                                .fields(
                                    "body",
                                    f -> f.fragmentSize(FRAGMENT_CHARS).numberOfFragments(1))),
            Map.class);
    List<SearchHitDto> hits = res.hits().hits().stream().map(SearchService::toHit).toList();
    long total = res.hits().total() == null ? hits.size() : res.hits().total().value();
    return new PageDto<>(hits, total, page, size);
  }

  @SuppressWarnings("rawtypes")
  private static SearchHitDto toHit(Hit<Map> hit) {
    Map src = hit.source() == null ? Map.of() : hit.source();
    List<String> fragments = hit.highlight().getOrDefault("body", List.of());
    String body = String.valueOf(src.getOrDefault("body", ""));
    return new SearchHitDto(
        UUID.fromString(String.valueOf(src.get("jobId"))),
        (String) src.get("company"),
        (String) src.get("title"),
        Status.valueOf(String.valueOf(src.get("status"))),
        fragments.isEmpty() ? PostgresSearch.snippet(body, "\u0000") : split(fragments.get(0)));
  }

  /** "a ␂Kafka␃ b ␂x␃" → before "a ", match "Kafka", after " b x" (plain text, tags removed). */
  static SearchHitDto.Snippet split(String fragment) {
    int start = fragment.indexOf(PRE);
    int end = fragment.indexOf(POST, start + 1);
    if (start < 0 || end < 0) {
      return new SearchHitDto.Snippet(strip(fragment), "", "");
    }
    return new SearchHitDto.Snippet(
        strip(fragment.substring(0, start)),
        fragment.substring(start + 1, end),
        strip(fragment.substring(end + 1)));
  }

  private static String strip(String s) {
    return s.replace(PRE, "").replace(POST, "");
  }
}
