package dev.skillsieve.core.application;

import dev.skillsieve.core.common.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

  private final ApplicationService service;

  public ApplicationController(ApplicationService service) {
    this.service = service;
  }

  @PatchMapping("/{id}")
  ApplicationDto update(
      @PathVariable UUID id, @Valid @RequestBody ApplicationPatch body, Authentication auth) {
    return service.update(CurrentUser.id(auth), id, body);
  }

  @GetMapping("/{id}/history")
  List<StatusEventDto> history(@PathVariable UUID id, Authentication auth) {
    return service.history(CurrentUser.id(auth), id);
  }
}
