package dev.skillsieve.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.skillsieve.core.IntegrationTest;
import dev.skillsieve.core.auth.Role;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** core-api ↔ skills-svc (stubbed): ARCHITECTURE §4.1 happy path and failure modes. */
class ExtractionIT extends IntegrationTest {

  private static final String RESULT =
      """
      {"company":"Northwind Labs","title":"Backend Engineer","location":"Singapore",
       "seniority":"MID","minYears":3,
       "skills":[
         {"slug":"java","raw":"Java 21","importance":"REQUIRED","evidence":"Java 21 services"},
         {"slug":"kubernetes","raw":"K8s","importance":"REQUIRED","evidence":null},
         {"slug":"aws","raw":"AWS","importance":"PREFERRED","evidence":"AWS is a plus"},
         {"slug":"not-in-taxonomy","raw":"x","importance":"PREFERRED","evidence":null}],
       "unmapped":["Quantum Basket Weaving"],"model":"gemini-test","promptVersion":"extract-v1",
       "cached":false,"futureField":true}
      """;

  private Cookie user;

  @BeforeEach
  void signIn() throws Exception {
    user = newUser(Role.USER);
  }

  private JsonNode create(String marker) throws Exception {
    return body(
        mvc.perform(
                ajax(post("/api/jobs"), user)
                    .content(json.writeValueAsString(Map.of("rawText", jobText(marker)))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  @Test
  void shouldStoreSkillsAndDetailsWhenSkillsSvcAnswers() throws Exception {
    SKILLS_SVC.respond(200, RESULT);

    JsonNode job = create("Northwind-" + UUID.randomUUID());

    assertThat(job.get("extractionStatus").asText()).isEqualTo("DONE");
    assertThat(job.get("company").asText()).isEqualTo("Northwind Labs");
    assertThat(job.get("seniority").asText()).isEqualTo("MID");
    assertThat(job.get("minYears").asInt()).isEqualTo(3);
    assertThat(job.get("skills")).hasSize(3);
    assertThat(SKILLS_SVC.lastToken()).isEqualTo("test-internal-token");
    assertThat(json.readTree(SKILLS_SVC.lastRequest()).get("textHash").asText()).hasSize(64);

    mvc.perform(get("/api/jobs").cookie(user))
        .andExpect(jsonPath("$.content[0].topSkills[0]").value("Java"))
        .andExpect(jsonPath("$.content[0].topSkills.length()").value(2));
  }

  @Test
  void shouldKeepJobPendingWhenSkillsSvcIsBusyThenRetry() throws Exception {
    JsonNode job = create("Busy-" + UUID.randomUUID());
    String id = job.get("id").asText();

    assertThat(job.get("extractionStatus").asText()).isEqualTo("PENDING");
    assertThat(job.get("extractionError").asText()).contains("Try again later");

    SKILLS_SVC.respond(200, RESULT);
    mvc.perform(ajax(post("/api/jobs/" + id + "/extract"), user)).andExpect(status().isAccepted());
    mvc.perform(get("/api/jobs/" + id).cookie(user))
        .andExpect(jsonPath("$.extractionStatus").value("DONE"))
        .andExpect(jsonPath("$.skills.length()").value(3));
  }

  @Test
  void shouldMarkFailedWhenSkillsSvcRejectsThePost() throws Exception {
    SKILLS_SVC.respond(422, "{\"detail\":\"bad\"}");

    JsonNode job = create("Rejected-" + UUID.randomUUID());

    assertThat(job.get("extractionStatus").asText()).isEqualTo("FAILED");
  }

  @Test
  void shouldNotOverwriteFieldsTheUserAlreadyEdited() throws Exception {
    JsonNode job = create("Edited-" + UUID.randomUUID());
    String id = job.get("id").asText();
    mvc.perform(ajax(patch("/api/jobs/" + id), user).content("{\"company\":\"My Label\"}"))
        .andExpect(status().isOk());

    SKILLS_SVC.respond(200, RESULT);
    mvc.perform(ajax(post("/api/jobs/" + id + "/extract"), user));

    mvc.perform(get("/api/jobs/" + id).cookie(user))
        .andExpect(jsonPath("$.company").value("My Label"))
        .andExpect(jsonPath("$.title").value("Backend Engineer"));
  }
}
