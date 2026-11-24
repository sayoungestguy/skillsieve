package dev.skillsieve.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TextNormaliserTest {

  /** Shared with skills-svc so both services hash job text the same way. */
  static Stream<JsonNode> vectors() throws IOException {
    JsonNode root =
        new ObjectMapper().readTree(Path.of("../../data/test-vectors/normalise.json").toFile());
    return StreamSupport.stream(root.get("cases").spliterator(), false);
  }

  @ParameterizedTest
  @MethodSource("vectors")
  void shouldMatchSharedTestVectors(JsonNode c) {
    String normalised = TextNormaliser.normalise(c.get("input").asText());

    assertThat(normalised).isEqualTo(c.get("normalised").asText());
    assertThat(TextNormaliser.sha256Hex(normalised)).isEqualTo(c.get("sha256").asText());
  }
}
