package dev.skillsieve.core.resume;

import dev.skillsieve.core.common.CurrentUser;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resume suggestions for one of my jobs. Web session only (extension tokens get 403). */
@RestController
@RequestMapping("/api/jobs/{jobId}/tailoring")
public class TailoringController {

  private final TailoringService service;
  private final TailoringRunner runner;

  public TailoringController(TailoringService service, TailoringRunner runner) {
    this.service = service;
    this.runner = runner;
  }

  @GetMapping
  ResponseEntity<TailoringDto> get(@PathVariable UUID jobId, Authentication auth) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.get(CurrentUser.id(auth), jobId));
  }

  /** Starts generating; poll GET until status is DONE or FAILED. */
  @PostMapping
  ResponseEntity<TailoringDto> request(@PathVariable UUID jobId, Authentication auth) {
    UUID userId = CurrentUser.id(auth);
    if (service.request(userId, jobId)) {
      runner.submit(userId, jobId);
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .cacheControl(CacheControl.noStore())
        .body(service.get(userId, jobId));
  }
}
