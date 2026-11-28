package dev.skillsieve.core.health;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public readiness for smoke tests through the ingress ({@code /api/health}). The actuator itself
 * lives on the internal management port; kubelet probes use {@code /livez} and {@code /readyz}.
 */
@RestController
public class HealthController {

  private final HealthEndpoint health;

  public HealthController(HealthEndpoint health) {
    this.health = health;
  }

  @GetMapping("/api/health")
  ResponseEntity<StatusDto> health() {
    HealthComponent readiness = health.healthForPath("readiness");
    Status status = readiness == null ? Status.UNKNOWN : readiness.getStatus();
    return ResponseEntity.status(Status.UP.equals(status) ? 200 : 503)
        .body(new StatusDto(status.getCode()));
  }

  /** Only the status: no component details leave the cluster. */
  public record StatusDto(String status) {}
}
