package dev.skillsieve.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchServiceTest {

  @Test
  void shouldSplitSnippetAroundCaseInsensitiveMatch() {
    var s = PostgresSearch.snippet("We use Kubernetes daily", "kubernetes");

    assertThat(s.before()).isEqualTo("We use ");
    assertThat(s.match()).isEqualTo("Kubernetes");
    assertThat(s.after()).isEqualTo(" daily");
  }

  @Test
  void shouldReturnPreviewWhenMatchIsOnlyInTitle() {
    var s = PostgresSearch.snippet("Body text", "acme");

    assertThat(s.before()).isEqualTo("Body text");
    assertThat(s.match()).isEmpty();
  }

  @Test
  void shouldSplitElasticsearchHighlightIntoPlainText() {
    var s = SearchService.split("we publish \u0002Kafka\u0003 events and \u0002Kafka\u0003 again");

    assertThat(s.before()).isEqualTo("we publish ");
    assertThat(s.match()).isEqualTo("Kafka");
    assertThat(s.after()).isEqualTo(" events and Kafka again");
  }
}
