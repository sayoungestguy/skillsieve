package dev.skillsieve.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.skillsieve.core.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/** Keycloak sign-in run by core-api (ADR-009), against a fake identity provider. */
class OidcLoginIT extends IntegrationTest {

  static final FakeOidcProvider IDP = FakeOidcProvider.start();

  @DynamicPropertySource
  static void oidc(DynamicPropertyRegistry registry) {
    registry.add("skillsieve.oidc.issuer", () -> FakeOidcProvider.ISSUER);
    registry.add("skillsieve.oidc.internal-url", IDP::baseUrl);
    registry.add("skillsieve.oidc.client-secret", () -> "s3cr3t+/=");
    registry.add("skillsieve.oidc.app-url", () -> "http://app.test");
  }

  /** Starts the flow; returns the flow cookie and the authorize URL's query params. */
  private record Started(Cookie cookie, Map<String, String> params) {}

  private Started start() throws Exception {
    MvcResult res =
        mvc.perform(get("/api/auth/oidc/login")).andExpect(status().isFound()).andReturn();
    String location = res.getResponse().getHeader("Location");
    assertThat(location).startsWith(FakeOidcProvider.ISSUER + "/protocol/openid-connect/auth?");
    Map<String, String> params =
        UriComponentsBuilder.fromUri(URI.create(location))
            .build()
            .getQueryParams()
            .toSingleValueMap()
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> java.net.URLDecoder.decode(e.getValue(), StandardCharsets.UTF_8)));
    Cookie cookie = res.getResponse().getCookie(OidcFlowCookie.NAME);
    assertThat(cookie).isNotNull();
    IDP.nonce(params.get("nonce"));
    return new Started(cookie, params);
  }

  private MvcResult callback(Started s, String state) throws Exception {
    return mvc.perform(
            get("/api/auth/oidc/callback")
                .param("code", "code-123")
                .param("state", state)
                .cookie(s.cookie()))
        .andExpect(status().isFound())
        .andReturn();
  }

  private static String location(MvcResult r) {
    return r.getResponse().getHeader("Location");
  }

  @Test
  void shouldRedirectToKeycloakWithPkceStateAndNonce() throws Exception {
    MvcResult res = mvc.perform(get("/api/auth/oidc/login")).andReturn();
    Started s = start();

    assertThat(s.params())
        .containsEntry("client_id", "skillsieve-web")
        .containsEntry("response_type", "code")
        .containsEntry("redirect_uri", "http://app.test/api/auth/oidc/callback")
        .containsEntry("code_challenge_method", "S256")
        .containsKeys("state", "nonce", "code_challenge");
    String setCookie = res.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie)
        .contains("HttpOnly")
        .contains("SameSite=Lax")
        .contains("Path=/api/auth/oidc");
  }

  @Test
  void shouldCreateUserAndSignIn() throws Exception {
    String sub = "kc-" + UUID.randomUUID();
    String email = "new-" + UUID.randomUUID() + "@test.local";
    IDP.user(sub, email, false);
    Started s = start();

    MvcResult res = callback(s, s.params().get("state"));

    assertThat(location(res)).isEqualTo("/");
    Cookie session = res.getResponse().getCookie(AuthCookie.NAME);
    assertThat(session).isNotNull();
    JsonNode me =
        body(mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk()).andReturn());
    assertThat(me.get("email").asText()).isEqualTo(email);
    assertThat(me.get("role").asText()).isEqualTo("USER");

    // PKCE verifier and client_secret_basic (form-encoded secret) reach the token endpoint.
    assertThat(IDP.lastTokenRequest()).contains("code_verifier=").contains("code=code-123");
    String basic =
        new String(
            Base64.getDecoder().decode(IDP.lastAuthHeader().substring(6)), StandardCharsets.UTF_8);
    assertThat(basic).isEqualTo("skillsieve-web:s3cr3t%2B%2F%3D");

    // Same Keycloak identity next time → same account, even if the email changed.
    IDP.user(sub, "changed-" + email, false);
    Started again = start();
    Cookie second =
        callback(again, again.params().get("state")).getResponse().getCookie(AuthCookie.NAME);
    mvc.perform(get("/api/me").cookie(second))
        .andExpect(jsonPath("$.id").value(me.get("id").asText()));
  }

  @Test
  void shouldLinkExistingAccountOnlyWithVerifiedEmail() throws Exception {
    String email = "linked-" + UUID.randomUUID() + "@test.local";
    users.save(
        new AppUser(email, passwordEncoder.encode("a-long-password"), Role.OWNER, Instant.now()));

    IDP.user("kc-" + UUID.randomUUID(), email, false);
    Started unverified = start();
    assertThat(location(callback(unverified, unverified.params().get("state"))))
        .isEqualTo("/login?oidc=unverified");

    IDP.user("kc-" + UUID.randomUUID(), email, true);
    Started verified = start();
    Cookie session =
        callback(verified, verified.params().get("state")).getResponse().getCookie(AuthCookie.NAME);
    mvc.perform(get("/api/me").cookie(session))
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("OWNER"));
    // The password still works after linking (fallback, ADR-009).
    login(email, "a-long-password");
  }

  @Test
  void shouldRejectWrongStateOrTamperedCookie() throws Exception {
    Started s = start();
    assertThat(location(callback(s, "not-the-state"))).isEqualTo("/login?oidc=expired");

    Cookie tampered =
        new Cookie(OidcFlowCookie.NAME, s.cookie().getValue().replaceFirst(".$", "A"));
    MvcResult res =
        mvc.perform(
                get("/api/auth/oidc/callback")
                    .param("code", "c")
                    .param("state", s.params().get("state"))
                    .cookie(tampered))
            .andReturn();
    assertThat(location(res)).isEqualTo("/login?oidc=expired");

    MvcResult noCookie =
        mvc.perform(get("/api/auth/oidc/callback").param("code", "c").param("state", "x"))
            .andReturn();
    assertThat(location(noCookie)).isEqualTo("/login?oidc=expired");
  }

  @Test
  void shouldRejectBadIdTokens() throws Exception {
    IDP.user("kc-" + UUID.randomUUID(), "bad-" + UUID.randomUUID() + "@test.local", true);

    Started wrongNonce = start();
    IDP.nonce("other-nonce");
    assertThat(location(callback(wrongNonce, wrongNonce.params().get("state"))))
        .isEqualTo("/login?oidc=error");

    Started wrongAud = start();
    IDP.customise(b -> b.audience("someone-else"));
    assertThat(location(callback(wrongAud, wrongAud.params().get("state"))))
        .isEqualTo("/login?oidc=error");

    Started wrongIss = start();
    IDP.customise(b -> b.issuer("http://evil.test/realms/skillsieve"));
    assertThat(location(callback(wrongIss, wrongIss.params().get("state"))))
        .isEqualTo("/login?oidc=error");

    Started expired = start();
    IDP.customise(b -> b.expirationTime(java.util.Date.from(Instant.now().minusSeconds(600))));
    assertThat(location(callback(expired, expired.params().get("state"))))
        .isEqualTo("/login?oidc=error");
  }

  @Test
  void shouldSendCancelledLoginBack() throws Exception {
    MvcResult res =
        mvc.perform(get("/api/auth/oidc/callback").param("error", "access_denied")).andReturn();
    assertThat(location(res)).isEqualTo("/login?oidc=cancelled");
  }

  @Test
  void shouldNotAllowPasswordLoginForKeycloakOnlyUser() throws Exception {
    String email = "kconly-" + UUID.randomUUID() + "@test.local";
    IDP.user("kc-" + UUID.randomUUID(), email, true);
    Started s = start();
    callback(s, s.params().get("state"));

    mvc.perform(
            post("/api/auth/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(Map.of("email", email, "password", "anything-at-all"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldAdvertiseKeycloakWithLogoutUrl() throws Exception {
    mvc.perform(get("/api/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.oidcEnabled").value(true))
        .andExpect(
            jsonPath("$.oidcLogoutUrl")
                .value(
                    FakeOidcProvider.ISSUER
                        + "/protocol/openid-connect/logout?client_id=skillsieve-web"
                        + "&post_logout_redirect_uri=http%3A%2F%2Fapp.test%2Flogin"));
  }
}
