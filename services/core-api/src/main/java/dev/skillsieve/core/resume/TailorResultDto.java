package dev.skillsieve.core.resume;

import java.util.List;

/** skills-svc /v1/tailor response; stored encrypted in tailoring.ciphertext. */
public record TailorResultDto(String model, String promptVersion, List<Suggestion> suggestions) {

  /** One rewritten bullet. {@code original} is copied from the resume by skills-svc. */
  public record Suggestion(String original, String suggested, List<String> skills, String reason) {}
}
