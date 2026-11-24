package dev.skillsieve.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchServiceTest {

  @Test
  void shouldSplitSnippetAroundCaseInsensitiveMatch() {
    var s = SearchService.snippet("We use Kubernetes daily", "kubernetes");

    assertThat(s.before()).isEqualTo("We use ");
    assertThat(s.match()).isEqualTo("Kubernetes");
    assertThat(s.after()).isEqualTo(" daily");
  }

  @Test
  void shouldReturnPreviewWhenMatchIsOnlyInTitle() {
    var s = SearchService.snippet("Body text", "acme");

    assertThat(s.before()).isEqualTo("Body text");
    assertThat(s.match()).isEmpty();
  }
}
