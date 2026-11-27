package dev.skillsieve.core.token;

import dev.skillsieve.core.common.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manage my extension tokens. Signed-in web session only (a token cannot mint tokens). */
@RestController
@RequestMapping("/api/tokens")
public class AccessTokenController {

  private final AccessTokenService service;

  public AccessTokenController(AccessTokenService service) {
    this.service = service;
  }

  @GetMapping
  List<AccessTokenDto> list(Authentication auth) {
    return service.list(CurrentUser.id(auth));
  }

  @PostMapping
  ResponseEntity<CreatedTokenDto> create(
      @Valid @RequestBody CreateTokenRequest body, Authentication auth) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(CurrentUser.id(auth), body.name()));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication auth) {
    service.revoke(CurrentUser.id(auth), id);
    return ResponseEntity.noContent().build();
  }
}
