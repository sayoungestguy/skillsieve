package dev.skillsieve.core.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.skillsieve.core.IntegrationTest;
import dev.skillsieve.core.auth.Role;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Resume suggestions per job (FR-12 part 3): async run, encrypted cache, stale detection. */
class TailoringIT extends IntegrationTest {

  private static final String EXTRACTION =
      """
      {"company":"Northwind Labs","title":"Backend Engineer","location":"Singapore",
       "seniority":"MID","minYears":3,
       "skills":[
         {"slug":"java","raw":"Java","importance":"REQUIRED","evidence":null},
         {"slug":"kubernetes","raw":"K8s","importance":"REQUIRED","evidence":null},
         {"slug":"aws","raw":"AWS","importance":"PREFERRED","evidence":null}],
       "unmapped":[],"model":"gemini-test","promptVersion":"extract-v1","cached":false}
      """;
  private static final String SUGGESTIONS =
      """
      {"model":"qwen2.5:1.5b","promptVersion":"tailor-v2","suggestions":[
        {"original":"Built REST APIs in Java","suggested":"Designed Java REST APIs for bookings",
         "skills":["Java"],"reason":"Shows Java."}]}
      """;
  private static final String CV = "Alex\n- Built REST APIs in Java\n- Wrote Python scripts";

  @Autowired private JdbcTemplate jdbc;
  private Cookie user;

  @BeforeEach
  void signIn() throws Exception {
    user = newUser(Role.USER);
    SKILLS_SVC.respondTailor(200, SUGGESTIONS);
  }

  private String job() throws Exception {
    SKILLS_SVC.respond(200, EXTRACTION);
    JsonNode job =
        body(
            mvc.perform(
                    ajax(post("/api/jobs"), user)
                        .content(
                            json.writeValueAsString(
                                Map.of("rawText", jobText("Tailor-" + UUID.randomUUID())))))
                .andExpect(status().isCreated())
                .andReturn());
    return job.get("id").asText();
  }

  private void saveResume(String text) throws Exception {
    mvc.perform(
            ajax(put("/api/resume"), user).content(json.writeValueAsString(Map.of("text", text))))
        .andExpect(status().isOk());
  }

  private JsonNode request(String jobId) throws Exception {
    return body(
        mvc.perform(ajax(post("/api/jobs/" + jobId + "/tailoring"), user))
            .andExpect(status().isAccepted())
            .andReturn());
  }

  /** Polls until the background run finishes (no Awaitility dependency). */
  private JsonNode awaitFinished(String jobId) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    while (true) {
      JsonNode t =
          body(mvc.perform(get("/api/jobs/" + jobId + "/tailoring").cookie(user)).andReturn());
      if (!"PENDING".equals(t.get("status").asText()) || Instant.now().isAfter(deadline)) {
        return t;
      }
      Thread.sleep(50);
    }
  }

  @Test
  void shouldGenerateSuggestionsInBackground() throws Exception {
    String id = job();
    saveResume(CV);
    mvc.perform(ajax(put("/api/me/skills/java"), user).content("{\"level\":2}"))
        .andExpect(status().is2xxSuccessful());

    assertThat(request(id).get("status").asText()).isIn("PENDING", "DONE");
    JsonNode done = awaitFinished(id);

    assertThat(done.get("status").asText()).isEqualTo("DONE");
    assertThat(done.get("stale").asBoolean()).isFalse();
    assertThat(done.get("model").asText()).isEqualTo("qwen2.5:1.5b");
    assertThat(done.get("suggestions").get(0).get("suggested").asText())
        .isEqualTo("Designed Java REST APIs for bookings");

    JsonNode sent = json.readTree(SKILLS_SVC.lastTailorRequest());
    assertThat(sent.get("resumeText").asText()).isEqualTo(CV);
    assertThat(sent.get("jobTitle").asText()).isEqualTo("Backend Engineer");
    assertThat(sent.get("skills")).hasSize(3);
    // Java is level 2, so only the other two count as missing.
    assertThat(sent.get("missingSkills").toString()).doesNotContain("Java");
    assertThat(sent.get("missingSkills")).hasSize(2);
  }

  @Test
  void shouldStoreSuggestionsEncrypted() throws Exception {
    String id = job();
    saveResume(CV);
    request(id);
    awaitFinished(id);

    byte[] stored =
        jdbc.queryForObject(
            "SELECT ciphertext FROM tailoring WHERE job_post_id = ?::uuid", byte[].class, id);
    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Designed");
  }

  @Test
  void shouldMarkStaleWhenResumeChanges() throws Exception {
    String id = job();
    saveResume(CV);
    request(id);
    awaitFinished(id);

    saveResume(CV + "\n- Led a team of three");

    mvc.perform(get("/api/jobs/" + id + "/tailoring").cookie(user))
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.stale").value(true));
  }

  @Test
  void shouldSaveFriendlyErrorWhenModelIsBusy() throws Exception {
    String id = job();
    saveResume(CV);
    SKILLS_SVC.respondTailor(503, "{\"detail\":\"Ollama timed out\"}");

    request(id);
    JsonNode failed = awaitFinished(id);

    assertThat(failed.get("status").asText()).isEqualTo("FAILED");
    assertThat(failed.get("error").asText()).isEqualTo(TailoringService.BUSY);
    assertThat(failed.get("suggestions")).isEmpty();
  }

  @Test
  void shouldExplainResumeWithoutBullets() throws Exception {
    String id = job();
    saveResume(CV);
    SKILLS_SVC.respondTailor(422, "{\"detail\":\"no resume lines to rewrite\"}");

    request(id);

    assertThat(awaitFinished(id).get("error").asText()).contains("no bullet points");
  }

  @Test
  void shouldNeedResumeAndOwnJob() throws Exception {
    String id = job();

    mvc.perform(ajax(post("/api/jobs/" + id + "/tailoring"), user))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Save your resume in Settings first."));
    mvc.perform(get("/api/jobs/" + id + "/tailoring").cookie(user))
        .andExpect(status().isNotFound());

    Cookie other = newUser(Role.USER);
    mvc.perform(ajax(post("/api/jobs/" + id + "/tailoring"), other))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldNeedSkillsFirst() throws Exception {
    SKILLS_SVC.respond(503, "{\"detail\":\"busy\"}");
    JsonNode job =
        body(
            mvc.perform(
                    ajax(post("/api/jobs"), user)
                        .content(
                            json.writeValueAsString(
                                Map.of("rawText", jobText("NoSkills-" + UUID.randomUUID())))))
                .andReturn());
    saveResume(CV);

    mvc.perform(ajax(post("/api/jobs/" + job.get("id").asText() + "/tailoring"), user))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("This job has no skills yet. Extract them first."));
  }

  @Test
  void shouldNotStartTwiceWhilePending() throws Exception {
    String id = job();
    saveResume(CV);
    jdbc.update(
        "INSERT INTO tailoring (user_id, job_post_id, status, resume_hmac, job_text_hash,"
            + " requested_at) SELECT r.user_id, j.id, 'PENDING', r.content_hmac, j.text_hash,"
            + " now() FROM resume r JOIN job_post j ON j.user_id = r.user_id WHERE j.id = ?::uuid",
        id);
    int before = SKILLS_SVC.tailorCalls();

    assertThat(request(id).get("status").asText()).isEqualTo("PENDING");
    Thread.sleep(300);

    assertThat(SKILLS_SVC.tailorCalls()).isEqualTo(before);
  }

  @Test
  void shouldShowExpiredPendingAsFailed() throws Exception {
    String id = job();
    saveResume(CV);
    jdbc.update(
        "INSERT INTO tailoring (user_id, job_post_id, status, resume_hmac, job_text_hash,"
            + " requested_at) SELECT r.user_id, j.id, 'PENDING', r.content_hmac, j.text_hash,"
            + " now() - interval '1 hour' FROM resume r JOIN job_post j ON j.user_id = r.user_id"
            + " WHERE j.id = ?::uuid",
        id);

    mvc.perform(get("/api/jobs/" + id + "/tailoring").cookie(user))
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.error").value("This took too long. Try again."));
  }

  @Autowired private TailoringService tailoringService;

  @Test
  void shouldFailRequestsLeftPendingByARestart() throws Exception {
    String id = job();
    saveResume(CV);
    jdbc.update(
        "INSERT INTO tailoring (user_id, job_post_id, status, resume_hmac, job_text_hash,"
            + " requested_at) SELECT r.user_id, j.id, 'PENDING', r.content_hmac, j.text_hash,"
            + " now() FROM resume r JOIN job_post j ON j.user_id = r.user_id WHERE j.id = ?::uuid",
        id);

    assertThat(tailoringService.failInterrupted()).isGreaterThanOrEqualTo(1);

    mvc.perform(get("/api/jobs/" + id + "/tailoring").cookie(user))
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.error").value(TailoringService.INTERRUPTED));
    // And the user can ask again straight away.
    assertThat(request(id).get("status").asText()).isIn("PENDING", "DONE");
    assertThat(awaitFinished(id).get("status").asText()).isEqualTo("DONE");
  }
}
