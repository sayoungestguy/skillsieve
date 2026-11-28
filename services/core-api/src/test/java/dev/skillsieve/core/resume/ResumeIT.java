package dev.skillsieve.core.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.skillsieve.core.IntegrationTest;
import dev.skillsieve.core.auth.Role;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Encrypted resume storage (FR-12, part 1). */
class ResumeIT extends IntegrationTest {

  private static final String CV = "Jane Doe\nBuilt Kafka pipelines handling 2M events/day.";

  @Autowired private JdbcTemplate jdbc;
  private Cookie user;

  @BeforeEach
  void signIn() throws Exception {
    user = newUser(Role.USER);
  }

  private String body(String text) throws Exception {
    return json.writeValueAsString(Map.of("text", text));
  }

  @Test
  void shouldSaveReadAndReplaceMyResume() throws Exception {
    mvc.perform(get("/api/resume").cookie(user)).andExpect(status().isNotFound());

    mvc.perform(ajax(put("/api/resume"), user).content(body("  " + CV + "  ")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.text").value(CV))
        .andExpect(jsonPath("$.charCount").value(CV.length()));

    mvc.perform(ajax(put("/api/resume"), user).content(body("Second version")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/resume").cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Second version"));
  }

  @Test
  void shouldStoreOnlyCiphertext() throws Exception {
    mvc.perform(ajax(put("/api/resume"), user).content(body(CV))).andExpect(status().isOk());

    byte[] stored =
        jdbc.queryForObject(
            "SELECT r.ciphertext FROM resume r JOIN app_user u ON u.id = r.user_id"
                + " ORDER BY r.updated_at DESC LIMIT 1",
            byte[].class);
    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Kafka", "Jane");
  }

  @Test
  void shouldKeepResumesPrivatePerUser() throws Exception {
    mvc.perform(ajax(put("/api/resume"), user).content(body(CV))).andExpect(status().isOk());

    mvc.perform(get("/api/resume").cookie(newUser(Role.USER))).andExpect(status().isNotFound());
  }

  @Test
  void shouldDeleteMyResume() throws Exception {
    mvc.perform(ajax(put("/api/resume"), user).content(body(CV))).andExpect(status().isOk());

    mvc.perform(ajax(delete("/api/resume"), user)).andExpect(status().isNoContent());
    mvc.perform(get("/api/resume").cookie(user)).andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectBlankOrHugeResume() throws Exception {
    mvc.perform(ajax(put("/api/resume"), user).content(body("   ")))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(ajax(put("/api/resume"), user).content(body("x".repeat(20_001))))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void shouldRequireSignInAndRefuseExtensionTokens() throws Exception {
    mvc.perform(get("/api/resume")).andExpect(status().isUnauthorized());

    String token =
        body(mvc.perform(
                    ajax(post("/api/tokens"), user)
                        .content(json.writeValueAsString(Map.of("name", "ext"))))
                .andReturn())
            .get("token")
            .asText();
    mvc.perform(get("/api/resume").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }
}
