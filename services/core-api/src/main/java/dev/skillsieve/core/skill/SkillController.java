package dev.skillsieve.core.skill;

import dev.skillsieve.core.common.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SkillController {

  private final SkillService service;

  public SkillController(SkillService service) {
    this.service = service;
  }

  @GetMapping("/skills")
  List<SkillDto> list(
      @RequestParam(required = false) @Nullable String q,
      @RequestParam(required = false) @Nullable String category,
      Authentication auth) {
    return service.list(CurrentUser.id(auth), q, category);
  }

  @PutMapping("/me/skills/{slug}")
  UserSkillDto setLevel(
      @PathVariable String slug, @Valid @RequestBody SetLevelRequest body, Authentication auth) {
    return service.setLevel(CurrentUser.id(auth), slug, body.level());
  }
}
