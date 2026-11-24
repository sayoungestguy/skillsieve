package dev.skillsieve.core.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch client, only when ES_URL is set. Without it, search and skill insights fall back to
 * Postgres (local dev, tests without ES). In-cluster only, no auth (ARCHITECTURE §7).
 */
@Configuration
@ConditionalOnExpression("!'${skillsieve.es-url:}'.isBlank()")
public class ElasticsearchConfig {

  private static final int CONNECT_TIMEOUT_MS = 2_000;
  private static final int SOCKET_TIMEOUT_MS = 5_000;

  @Bean(destroyMethod = "close")
  RestClient esRestClient(AppProperties props) {
    return RestClient.builder(HttpHost.create(props.esUrl()))
        .setRequestConfigCallback(
            c -> c.setConnectTimeout(CONNECT_TIMEOUT_MS).setSocketTimeout(SOCKET_TIMEOUT_MS))
        .build();
  }

  @Bean
  ElasticsearchClient elasticsearchClient(RestClient restClient, ObjectMapper objectMapper) {
    return new ElasticsearchClient(
        new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper.copy())));
  }
}
