package dev.skillsieve.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.skillsieve.core.common.PageDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SearchFallbackTest {

  @Test
  @SuppressWarnings("unchecked")
  void shouldAnswerFromPostgresWhenElasticsearchFails() throws IOException {
    ElasticsearchClient client = mock(ElasticsearchClient.class);
    when(client.search(any(Function.class), eq(Map.class))).thenThrow(new IOException("down"));
    ObjectProvider<ElasticsearchClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(client);
    PostgresSearch postgres = mock(PostgresSearch.class);
    UUID user = UUID.randomUUID();
    PageDto<SearchHitDto> fromPostgres = new PageDto<>(List.of(), 0, 0, 20);
    when(postgres.search(user, "kafka", 0, 20)).thenReturn(fromPostgres);

    JobIndex index = mock(JobIndex.class);
    when(index.ready()).thenReturn(true);
    ObjectProvider<JobIndex> indexProvider = mock(ObjectProvider.class);
    when(indexProvider.getIfAvailable()).thenReturn(index);

    var result = new SearchService(provider, postgres, indexProvider).search(user, "kafka", 0, 20);

    assertThat(result).isSameAs(fromPostgres);
    verify(postgres).search(user, "kafka", 0, 20);
  }
}
