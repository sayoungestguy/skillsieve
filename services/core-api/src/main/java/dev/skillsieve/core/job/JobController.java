package dev.skillsieve.core.job;

import dev.skillsieve.core.application.Status;
import dev.skillsieve.core.common.CurrentUser;
import dev.skillsieve.core.common.PageDto;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

  private final JobService service;

  public JobController(JobService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<JobDto> create(@Valid @RequestBody CreateJobRequest body, Authentication auth) {
    JobDto job = service.create(CurrentUser.id(auth), body);
    return ResponseEntity.created(URI.create("/api/jobs/" + job.id())).body(job);
  }

  @GetMapping
  PageDto<JobSummaryDto> board(
      @RequestParam(required = false) @Nullable Status status,
      @RequestParam(required = false) @Nullable String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      Authentication auth) {
    return service.board(CurrentUser.id(auth), status, q, page, size);
  }

  @GetMapping("/{id}")
  JobDto get(@PathVariable UUID id, Authentication auth) {
    return service.get(CurrentUser.id(auth), id);
  }

  @PatchMapping("/{id}")
  JobDto update(
      @PathVariable UUID id, @Valid @RequestBody UpdateJobRequest body, Authentication auth) {
    return service.update(CurrentUser.id(auth), id, body);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
    service.delete(CurrentUser.id(auth), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/extract")
  ResponseEntity<Void> extract(@PathVariable UUID id, Authentication auth) {
    service.requestExtraction(CurrentUser.id(auth), id);
    return ResponseEntity.accepted().build();
  }
}
