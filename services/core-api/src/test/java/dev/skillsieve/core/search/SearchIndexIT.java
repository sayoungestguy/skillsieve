package dev.skillsieve.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import dev.skillsieve.core.IntegrationTest;
import dev.skillsieve.core.auth.Role;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Postgres → Elasticsearch sync, ES search, ES gap aggregation, reindex (TECHNICAL_DESIGN §4). */
class SearchIndexIT extends IntegrationTest {

  private static final String EXTRACTED =
      """
      {"company":"Zephyr Analytics","title":"Data Engineer","location":null,"seniority":"SENIOR",
       "minYears":5,"skills":[
         {"slug":"python","raw":"Python","importance":"REQUIRED","evidence":null},
         {"slug":"kafka","raw":"Kafka","importance":"PREFERRED","evidence":null}],
       "unmapped":[],"model":"m","promptVersion":"extract-v1","cached":false}
      """;

  @Autowired private ElasticsearchClient es;

  private JsonNode create(Cookie user, String marker) throws Exception {
    return body(
        mvc.perform(
                ajax(post("/api/jobs"), user)
                    .content(json.writeValueAsString(Map.of("rawText", jobText(marker)))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long docCount(String jobId) throws Exception {
    return es.count(c -> c.index(JobIndex.ALIAS).query(q -> q.ids(i -> i.values(jobId)))).count();
  }

  @Test
  void shouldIndexSearchAndHighlightFromElasticsearch() throws Exception {
    Cookie user = newUser(Role.USER);
    String marker = "Quokkasoft" + UUID.randomUUID().toString().substring(0, 6);
    SKILLS_SVC.respond(200, EXTRACTED);
    JsonNode job = create(user, marker);
    String id = job.get("id").asText();

    assertThat(docCount(id)).isOne();
    mvc.perform(get("/api/search").param("q", marker).cookie(user))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value(id))
        .andExpect(jsonPath("$.content[0].company").value("Zephyr Analytics"))
        .andExpect(jsonPath("$.content[0].snippet.match").value(marker))
        .andExpect(jsonPath("$.content[0].snippet.before").value("Senior Backend Engineer at "));

    // "python" is only in the extracted skills, not in the post text: only ES can find it.
    mvc.perform(get("/api/search").param("q", "python").cookie(user))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value(id));

    // Status changes reach the index.
    mvc.perform(
            ajax(patch("/api/applications/" + job.at("/application/id").asText()), user)
                .content("{\"status\":\"INTERVIEW\",\"version\":0}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/search").param("q", marker).cookie(user))
        .andExpect(jsonPath("$.content[0].status").value("INTERVIEW"));

    // Another user never sees it.
    mvc.perform(get("/api/search").param("q", marker).cookie(newUser(Role.USER)))
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(ajax(delete("/api/jobs/" + id), user)).andExpect(status().isNoContent());
    assertThat(docCount(id)).isZero();
  }

  @Test
  void shouldAggregateSkillGapsInElasticsearch() throws Exception {
    Cookie user = newUser(Role.USER);
    SKILLS_SVC.respond(200, EXTRACTED);
    create(user, "Agg-A-" + UUID.randomUUID());
    SKILLS_SVC.respond(503, "{}");
    create(user, "Agg-B-" + UUID.randomUUID()); // no skills yet

    mvc.perform(get("/api/insights/skills").cookie(user))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].slug").value("kafka")) // 50% each: tie broken by name
        .andExpect(jsonPath("$[0].preferredPct").value(50))
        .andExpect(jsonPath("$[0].requiredPct").value(0))
        .andExpect(jsonPath("$[1].slug").value("python"))
        .andExpect(jsonPath("$[1].requiredPct").value(50))
        .andExpect(jsonPath("$[1].isGap").value(true));

    mvc.perform(get("/api/insights/skills").param("status", "OFFER").cookie(user))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/insights/skills").param("from", "2999-01-01").cookie(user))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void shouldLetOnlyTheOwnerReindex() throws Exception {
    Cookie owner = loginOwner();
    create(owner, "Reindex-" + UUID.randomUUID());

    mvc.perform(ajax(post("/api/admin/reindex"), newUser(Role.USER)))
        .andExpect(status().isForbidden());
    mvc.perform(ajax(post("/api/admin/reindex"), owner))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.indexed").isNumber());

    var aliases = es.indices().getAlias(a -> a.name(JobIndex.ALIAS)).result().keySet();
    assertThat(aliases).hasSize(1);
    mvc.perform(get("/api/search").param("q", "Reindex").cookie(owner))
        .andExpect(jsonPath("$.totalElements").isNumber());
  }
}
