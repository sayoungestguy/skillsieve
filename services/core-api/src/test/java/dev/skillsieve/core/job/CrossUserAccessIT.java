package dev.skillsieve.core.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.junit.jupiter.api.Test;

/** CODING_STANDARDS §9.3: another user's resources must look like they do not exist (404). */
class CrossUserAccessIT extends IntegrationTest {

  @Test
  void shouldHideOwnersJobFromAnotherUser() throws Exception {
    Cookie owner = loginOwner();
    String text = jobText("Private-" + UUID.randomUUID());
    JsonNode job =
        body(
            mvc.perform(
                    ajax(post("/api/jobs"), owner)
                        .content(json.writeValueAsString(Map.of("rawText", text))))
                .andExpect(status().isCreated())
                .andReturn());
    String jobId = job.get("id").asText();
    String appId = job.at("/application/id").asText();

    Cookie stranger = newUser(Role.USER);

    mvc.perform(get("/api/jobs/" + jobId).cookie(stranger)).andExpect(status().isNotFound());
    mvc.perform(ajax(patch("/api/jobs/" + jobId), stranger).content("{\"title\":\"x\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(ajax(delete("/api/jobs/" + jobId), stranger)).andExpect(status().isNotFound());
    mvc.perform(ajax(patch("/api/applications/" + appId), stranger).content("{\"version\":0}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/applications/" + appId + "/history").cookie(stranger))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/jobs").cookie(stranger)).andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/search").param("q", "Private").cookie(stranger))
        .andExpect(jsonPath("$.totalElements").value(0));

    // The same text is not a duplicate for a different user.
    mvc.perform(
            ajax(post("/api/jobs"), stranger)
                .content(json.writeValueAsString(Map.of("rawText", text))))
        .andExpect(status().isCreated());
  }
}
