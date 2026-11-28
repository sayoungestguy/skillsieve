package dev.skillsieve.core.resume;

import dev.skillsieve.core.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** My resume. Web session only: extension tokens are limited to saving jobs (AccessTokenFilter). */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

  private final ResumeService service;

  public ResumeController(ResumeService service) {
    this.service = service;
  }

  @GetMapping
  ResponseEntity<ResumeDto> get(Authentication auth) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.get(CurrentUser.id(auth)));
  }

  @PutMapping
  ResponseEntity<ResumeDto> save(@Valid @RequestBody SaveResumeRequest body, Authentication auth) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.save(CurrentUser.id(auth), body.text()));
  }

  @DeleteMapping
  ResponseEntity<Void> delete(Authentication auth) {
    service.delete(CurrentUser.id(auth));
    return ResponseEntity.noContent().build();
  }
}
