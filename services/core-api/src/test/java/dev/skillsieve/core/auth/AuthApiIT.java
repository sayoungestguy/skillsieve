package dev.skillsieve.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.skillsieve.core.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthApiIT extends IntegrationTest {

  @Test
  void shouldIssueHardenedCookieAndReturnMe() throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", OWNER, "password", PASSWORD))))
            .andExpect(status().isNoContent())
            .andReturn();
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

    assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api");

    Cookie cookie = result.getResponse().getCookie(AuthCookie.NAME);
    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(OWNER))
        .andExpect(jsonPath("$.role").value("OWNER"))
        .andExpect(jsonPath("$.viaKeycloak").value(false));
  }

  @Test
  void shouldReturn401ForWrongPassword() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", OWNER, "password", "nope"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("Login failed"));
  }

  @Test
  void shouldRejectForgedToken() throws Exception {
    mvc.perform(get("/api/me").cookie(new Cookie(AuthCookie.NAME, "eyJhbGciOiJIUzI1NiJ9.e30.x")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldClearCookieOnLogout() throws Exception {
    var result =
        mvc.perform(post("/api/auth/logout").header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  @Test
  void shouldLetDemoUserReadButNotWrite() throws Exception {
    Cookie demo = newUser(Role.DEMO);

    mvc.perform(get("/api/jobs").cookie(demo)).andExpect(status().isOk());
    mvc.perform(
            ajax(post("/api/jobs"), demo)
                .content(json.writeValueAsString(Map.of("rawText", jobText("Demo")))))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldExposeHealthWithoutLogin() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .json("{\"status\":\"UP\"}", true));
    mvc.perform(get("/livez")).andExpect(status().isOk());
    mvc.perform(get("/readyz")).andExpect(status().isOk());
  }
}
