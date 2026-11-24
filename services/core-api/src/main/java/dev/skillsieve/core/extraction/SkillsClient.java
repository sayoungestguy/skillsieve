package dev.skillsieve.core.extraction;

import dev.skillsieve.core.config.AppProperties;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP client for skills-svc POST /v1/extract (internal, X-Internal-Token). */
@Component
public class SkillsClient {

  static final String TOKEN_HEADER = "X-Internal-Token";
  private static final int CONNECT_TIMEOUT_MS = 2_000;

  private final RestClient http;
  private final boolean enabled;

  public SkillsClient(RestClient.Builder builder, AppProperties props) {
    this.enabled = !props.skillsSvcUrl().isBlank();
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout((int) props.extractionTimeout().toMillis());
    this.http =
        builder
            .baseUrl(enabled ? props.skillsSvcUrl() : "http://skills-svc.invalid")
            .requestFactory(factory)
            .defaultHeader(TOKEN_HEADER, props.internalToken())
            .build();
  }

  /** Empty when skills-svc is not configured (SKILLS_SVC_URL unset). */
  public Optional<ExtractionResultDto> extract(String textHash, String text) {
    if (!enabled) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        http.post()
            .uri("/v1/extract")
            .body(Map.of("textHash", textHash, "text", text))
            .retrieve()
            .body(ExtractionResultDto.class));
  }
}
