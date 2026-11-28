package dev.skillsieve.core.resume;

import dev.skillsieve.core.config.AppProperties;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * skills-svc POST /v1/tailor. skills-svc answers with its local Ollama model only, never Gemini
 * (CLAUDE.md C5), so resume text may be sent here. Long read timeout: CPU inference is slow.
 */
@Component
public class TailorClient {

  private static final int CONNECT_TIMEOUT_MS = 2_000;

  /** Request body (camelCase, TECHNICAL_DESIGN §6). */
  public record TailorRequest(
      String resumeText,
      @Nullable String jobTitle,
      @Nullable String company,
      List<JobSkill> skills,
      List<String> missingSkills) {}

  public record JobSkill(String name, String importance) {}

  private final RestClient http;
  private final boolean enabled;

  public TailorClient(RestClient.Builder builder, AppProperties app, TailorProperties props) {
    this.enabled = !app.skillsSvcUrl().isBlank();
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout((int) props.timeout().toMillis());
    this.http =
        builder
            .baseUrl(enabled ? app.skillsSvcUrl() : "http://skills-svc.invalid")
            .requestFactory(factory)
            .defaultHeader("X-Internal-Token", app.internalToken())
            .build();
  }

  public boolean enabled() {
    return enabled;
  }

  public TailorResultDto tailor(TailorRequest request) {
    TailorResultDto result =
        http.post().uri("/v1/tailor").body(request).retrieve().body(TailorResultDto.class);
    if (result == null) {
      throw new IllegalStateException("empty response from skills-svc");
    }
    return result;
  }
}
