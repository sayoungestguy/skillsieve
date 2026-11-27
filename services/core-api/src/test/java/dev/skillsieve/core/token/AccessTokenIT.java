package dev.skillsieve.core.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Personal access tokens for the Chrome extension (TECHNICAL_DESIGN §14). */
class AccessTokenIT extends IntegrationTest {

  private Cookie user;

  @BeforeEach
  void signIn() throws Exception {
    user = newUser(Role.USER);
  }

  private JsonNode createToken(Cookie who) throws Exception {
    return body(
        mvc.perform(
                ajax(post("/api/tokens"), who)
                    .content(json.writeValueAsString(Map.of("name", "Chrome on laptop"))))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private static MockHttpServletRequestBuilder bearer(
      MockHttpServletRequestBuilder req, String token) {
    return req.header("Authorization", "Bearer " + token)
        .header("X-Requested-With", "SkillSieveExtension")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private String saveBody() throws Exception {
    return json.writeValueAsString(
        Map.of(
            "rawText",
            jobText("Extension-" + UUID.randomUUID()),
            "sourceUrl",
            "https://www.linkedin.com/jobs/view/123"));
  }

  @Test
  void shouldShowTokenOnceAndListOnlyItsPrefix() throws Exception {
    JsonNode created = createToken(user);
    String token = created.get("token").asText();

    assertThat(token).startsWith("ssv_").hasSizeGreaterThan(40);
    assertThat(created.get("info").get("prefix").asText()).isEqualTo(token.substring(0, 12));

    String list =
        mvc.perform(get("/api/tokens").cookie(user)).andReturn().getResponse().getContentAsString();
    assertThat(list).contains("Chrome on laptop").doesNotContain(token);
  }

  @Test
  void shouldSaveJobForTokenOwner() throws Exception {
    String token = createToken(user).get("token").asText();

    JsonNode job =
        body(
            mvc.perform(bearer(post("/api/jobs"), token).content(saveBody()))
                .andExpect(status().isCreated())
                .andReturn());

    mvc.perform(get("/api/jobs/" + job.get("id").asText()).cookie(user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceUrl").value("https://www.linkedin.com/jobs/view/123"));
    mvc.perform(bearer(get("/api/me"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"));
    mvc.perform(get("/api/tokens").cookie(user))
        .andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty());
  }

  @Test
  void shouldOnlyAllowSavingJobs() throws Exception {
    String token = createToken(user).get("token").asText();

    mvc.perform(bearer(get("/api/jobs"), token)).andExpect(status().isForbidden());
    mvc.perform(bearer(get("/api/insights/skills"), token)).andExpect(status().isForbidden());
    mvc.perform(
            bearer(post("/api/tokens"), token)
                .content(json.writeValueAsString(Map.of("name", "sneaky"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectRevokedAndUnknownTokens() throws Exception {
    JsonNode created = createToken(user);
    String token = created.get("token").asText();

    mvc.perform(ajax(delete("/api/tokens/" + created.get("info").get("id").asText()), user))
        .andExpect(status().isNoContent());

    mvc.perform(bearer(post("/api/jobs"), token).content(saveBody()))
        .andExpect(status().isUnauthorized());
    mvc.perform(bearer(post("/api/jobs"), "ssv_" + "x".repeat(43)).content(saveBody()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/tokens").cookie(user)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void shouldStillRequireRequestedWithHeader() throws Exception {
    String token = createToken(user).get("token").asText();

    mvc.perform(
            post("/api/jobs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldNotRevokeSomeoneElsesToken() throws Exception {
    String id = createToken(user).get("info").get("id").asText();
    Cookie other = newUser(Role.USER);

    mvc.perform(ajax(delete("/api/tokens/" + id), other)).andExpect(status().isNotFound());
  }

  @Test
  void shouldCapActiveTokens() throws Exception {
    for (int i = 0; i < AccessTokenService.MAX_ACTIVE; i++) {
      createToken(user);
    }
    mvc.perform(
            ajax(post("/api/tokens"), user)
                .content(json.writeValueAsString(Map.of("name", "one too many"))))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldNotLetDemoUsersCreateTokens() throws Exception {
    Cookie demo = newUser(Role.DEMO);

    mvc.perform(
            ajax(post("/api/tokens"), demo)
                .content(json.writeValueAsString(Map.of("name", "demo"))))
        .andExpect(status().isForbidden());
  }
}
