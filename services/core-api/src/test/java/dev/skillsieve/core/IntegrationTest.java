package dev.skillsieve.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillsieve.core.auth.AppUser;
import dev.skillsieve.core.auth.AppUserRepository;
import dev.skillsieve.core.auth.AuthCookie;
import dev.skillsieve.core.auth.Role;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/** Boots the app against a real Postgres 16 (Testcontainers), shared by all *IT classes. */
@SpringBootTest(
    properties = {
      "skillsieve.allow-random-jwt-secret=true",
      "skillsieve.internal-token=test-internal-token",
      "skillsieve.extraction-timeout=2s",
      "skillsieve.owner-email=owner@test.local",
      "skillsieve.owner-password=owner-password",
      "skillsieve.login-attempts-per-minute=1000"
    })
@AutoConfigureMockMvc
public abstract class IntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  /** Stands in for skills-svc. Default answer: 503 (busy), so new jobs stay PENDING. */
  protected static final SkillsSvcStub SKILLS_SVC = SkillsSvcStub.start();

  /** Single-node ES 8, same version as the cluster; security off like in-cluster (ADR-004). */
  protected static final ElasticsearchContainer ELASTICSEARCH =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.18.8")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

  static {
    POSTGRES.start();
    ELASTICSEARCH.start();
  }

  @DynamicPropertySource
  static void skillsSvc(DynamicPropertyRegistry registry) {
    registry.add("skillsieve.skills-svc-url", SKILLS_SVC::baseUrl);
    registry.add("skillsieve.es-url", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
  }

  @AfterEach
  void resetSkillsSvc() {
    SKILLS_SVC.respond(503, "{\"detail\":\"busy\"}");
  }

  protected static final String OWNER = "owner@test.local";
  protected static final String PASSWORD = "owner-password";

  /** Job text long enough to pass validation (≥ 200 chars). */
  protected static String jobText(String marker) {
    return ("Senior Backend Engineer at " + marker + ". ")
        + "You will build Java and Spring Boot services, run them on Kubernetes, "
        + "and work with PostgreSQL and Kafka. Experience with Docker and CI/CD pipelines "
        + "is required; Terraform and AWS are a plus. ".repeat(2);
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected AppUserRepository users;
  @Autowired protected PasswordEncoder passwordEncoder;

  protected Cookie login(String email, String password) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new Login(email, password))))
            .andExpect(status().isNoContent())
            .andReturn();
    return result.getResponse().getCookie(AuthCookie.NAME);
  }

  protected Cookie loginOwner() throws Exception {
    return login(OWNER, PASSWORD);
  }

  /** Creates another account and signs in as it. */
  protected Cookie newUser(Role role) throws Exception {
    String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
    users.save(new AppUser(email, passwordEncoder.encode("pw-" + email), role, Instant.now()));
    return login(email, "pw-" + email);
  }

  protected MockHttpServletRequestBuilder ajax(MockHttpServletRequestBuilder req, Cookie auth) {
    return req.cookie(auth)
        .header("X-Requested-With", "XMLHttpRequest")
        .contentType(MediaType.APPLICATION_JSON);
  }

  protected JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private record Login(String email, String password) {}
}
