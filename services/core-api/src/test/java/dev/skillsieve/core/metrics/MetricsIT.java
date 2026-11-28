package dev.skillsieve.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.skillsieve.core.IntegrationTest;
import dev.skillsieve.core.auth.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Real ports: Prometheus metrics live on the management port only (TECHNICAL_DESIGN §11), and the
 * app port keeps /api/livez and /api/readyz for probes and smoke tests.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "skillsieve.allow-random-jwt-secret=true",
      "skillsieve.internal-token=test-internal-token",
      "skillsieve.extraction-timeout=2s",
      "skillsieve.owner-email=owner@test.local",
      "skillsieve.owner-password=owner-password",
      "skillsieve.login-attempts-per-minute=1000",
      "management.server.port=0"
    })
@AutoConfigureObservability // tests turn metrics exporters off by default
class MetricsIT extends IntegrationTest {

  @LocalServerPort int appPort;
  @LocalManagementPort int managementPort;

  private final RestClient http = RestClient.create();

  private String scrape() {
    return http.get()
        .uri("http://localhost:" + managementPort + "/actuator/prometheus")
        .retrieve()
        .body(String.class);
  }

  @Test
  void shouldExposeCustomMetricsOnManagementPort() throws Exception {
    mvc.perform(
            ajax(post("/api/jobs"), newUser(Role.USER))
                .content(
                    json.writeValueAsString(
                        Map.of("rawText", jobText("Metrics-" + UUID.randomUUID())))))
        .andExpect(status().isCreated());

    String metrics = scrape();

    assertThat(metrics)
        .contains("skillsieve_jobs_saved_total{application=\"core-api\"")
        .contains("skillsieve_extractions_total{application=\"core-api\",result=\"retry_later\"")
        .contains("http_server_requests_seconds_bucket")
        .contains("jvm_memory_used_bytes");
  }

  @Test
  void shouldKeepMetricsOffTheAppPortAndHealthOnIt() {
    String app = "http://localhost:" + appPort;

    for (String path : new String[] {"/api/health", "/livez", "/readyz"}) {
      assertThat(http.get().uri(app + path).retrieve().toBodilessEntity().getStatusCode().value())
          .as(path)
          .isEqualTo(200);
    }
    for (String path : new String[] {"/actuator/prometheus", "/api/actuator/prometheus"}) {
      int code;
      try {
        code = http.get().uri(app + path).retrieve().toBodilessEntity().getStatusCode().value();
      } catch (HttpClientErrorException e) {
        code = e.getStatusCode().value();
      }
      assertThat(code).as(path).isIn(401, 403, 404);
    }
  }
}
