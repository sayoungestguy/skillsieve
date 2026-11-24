package dev.skillsieve.core.insight;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.skillsieve.core.IntegrationTest;
import dev.skillsieve.core.auth.Role;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class InsightsAndSkillsIT extends IntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  /** Stands in for skills-svc (Days 6–8), which will write job_skill rows. */
  private void tag(String jobId, String slug, String importance) {
    jdbc.update(
        "INSERT INTO skillsieve.job_skill (job_post_id, skill_id, importance) "
            + "SELECT ?::uuid, id, ? FROM skillsieve.skill WHERE slug = ?",
        jobId,
        importance,
        slug);
  }

  private JsonNode create(Cookie user, String marker) throws Exception {
    return body(
        mvc.perform(
                ajax(post("/api/jobs"), user)
                    .content(json.writeValueAsString(Map.of("rawText", jobText(marker)))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  @Test
  void shouldComputeSummaryGapsAndSkillLevels() throws Exception {
    Cookie user = newUser(Role.USER);
    JsonNode a = create(user, "A-" + UUID.randomUUID());
    JsonNode b = create(user, "B-" + UUID.randomUUID());
    tag(a.get("id").asText(), "kubernetes", "REQUIRED");
    tag(b.get("id").asText(), "kubernetes", "PREFERRED");
    tag(a.get("id").asText(), "java", "REQUIRED");
    mvc.perform(
            ajax(patch("/api/applications/" + a.at("/application/id").asText()), user)
                .content("{\"status\":\"INTERVIEW\",\"version\":0}"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/insights/summary").cookie(user))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.applied").value(1))
        .andExpect(jsonPath("$.interviews").value(1))
        .andExpect(jsonPath("$.interviewRate").value(1.0));

    mvc.perform(get("/api/insights/skills").cookie(user))
        .andExpect(jsonPath("$[0].slug").value("kubernetes"))
        .andExpect(jsonPath("$[0].requiredPct").value(50))
        .andExpect(jsonPath("$[0].preferredPct").value(50))
        .andExpect(jsonPath("$[0].totalPct").value(100))
        .andExpect(jsonPath("$[0].isGap").value(true));

    mvc.perform(get("/api/insights/skills").param("status", "INTERVIEW").cookie(user))
        .andExpect(jsonPath("$.length()").value(2));

    mvc.perform(ajax(put("/api/me/skills/kubernetes"), user).content("{\"level\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value(3));
    mvc.perform(ajax(put("/api/me/skills/kubernetes"), user).content("{\"level\":2}"))
        .andExpect(jsonPath("$.level").value(2));

    mvc.perform(get("/api/insights/skills").cookie(user))
        .andExpect(jsonPath("$[0].myLevel").value(2))
        .andExpect(jsonPath("$[0].isGap").value(false));

    mvc.perform(get("/api/skills").param("q", "k8s").cookie(user))
        .andExpect(jsonPath("$[0].slug").value("kubernetes"))
        .andExpect(jsonPath("$[0].frequencyPct").value(100))
        .andExpect(jsonPath("$[0].myLevel").value(2));

    mvc.perform(get("/api/jobs/" + a.get("id").asText()).cookie(user))
        .andExpect(jsonPath("$.skills[0].slug").value("java"))
        .andExpect(jsonPath("$.skills[0].importance").value("REQUIRED"));

    mvc.perform(get("/api/jobs").cookie(user)).andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  void shouldValidateSkillLevelAndSlug() throws Exception {
    Cookie user = newUser(Role.USER);

    mvc.perform(ajax(put("/api/me/skills/java"), user).content("{\"level\":4}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(ajax(put("/api/me/skills/no-such-skill"), user).content("{\"level\":1}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/skills").param("category", "devops").cookie(user))
        .andExpect(jsonPath("$[?(@.slug == 'docker')]").exists())
        .andExpect(jsonPath("$[?(@.slug == 'java')]").doesNotExist());
  }

  @Test
  void shouldFindJobTextInSearch() throws Exception {
    Cookie user = newUser(Role.USER);
    String marker = "Zebracorp" + UUID.randomUUID().toString().substring(0, 6);
    create(user, marker);

    mvc.perform(get("/api/search").param("q", marker.toLowerCase()).cookie(user))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].snippet.match").value(marker));
    mvc.perform(get("/api/search").param("q", " ").cookie(user))
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}
