package dev.skillsieve.core.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Owns the job-posts index: create on first start, keep documents in sync, and rebuild into a new
 * index with an atomic alias swap (TECHNICAL_DESIGN §4). Index failures are logged, never thrown:
 * Postgres stays the source of truth and {@code POST /api/admin/reindex} repairs drift.
 */
@Component
public class JobIndex {

  public static final String ALIAS = "job-posts-current";
  static final String PREFIX = "job-posts-v1";
  private static final int BULK_SIZE = 200;
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
  private static final Logger log = LoggerFactory.getLogger(JobIndex.class);

  private final ObjectProvider<ElasticsearchClient> es;
  private final JobDocuments documents;
  private final Clock clock;

  /**
   * True once the alias exists. Until then nothing is written, so ES never auto-creates an unmapped
   * index named like the alias.
   */
  private final AtomicBoolean ready = new AtomicBoolean(false);

  public JobIndex(ObjectProvider<ElasticsearchClient> es, JobDocuments documents, Clock clock) {
    this.es = es;
    this.documents = documents;
    this.clock = clock;
  }

  public boolean enabled() {
    return es.getIfAvailable() != null;
  }

  public boolean ready() {
    return ready.get();
  }

  /**
   * Makes sure the index exists, building it from Postgres if the alias is missing. Runs at startup
   * and every 15 s until it succeeds, so core-api can start before Elasticsearch is up.
   */
  @Scheduled(initialDelay = 0, fixedDelay = 15_000)
  public void ensureIndex() {
    ElasticsearchClient client = es.getIfAvailable();
    if (client == null || ready.get()) {
      return;
    }
    try {
      if (!client.indices().existsAlias(a -> a.name(ALIAS)).value()) {
        log.info("search.index alias missing, building {}", ALIAS);
        reindex();
      }
      ready.set(true);
      log.info("search.index ready alias={}", ALIAS);
    } catch (IOException | RuntimeException e) {
      log.warn("search.index not ready yet, retrying: {}", e.getMessage());
    }
  }

  /** Indexes one job (or removes it). Waits for refresh so the change is searchable at once. */
  public void sync(UUID jobId, boolean deleted) {
    ElasticsearchClient client = es.getIfAvailable();
    if (client == null || !ready.get()) {
      return; // the build in ensureIndex() will include this change
    }
    try {
      if (deleted) {
        client.delete(d -> d.index(ALIAS).id(jobId.toString()).refresh(Refresh.WaitFor));
        return;
      }
      var doc = documents.find(jobId);
      if (doc.isPresent()) {
        client.index(
            i -> i.index(ALIAS).id(jobId.toString()).document(doc.get()).refresh(Refresh.WaitFor));
      }
    } catch (IOException | RuntimeException e) {
      log.warn("search.sync failed jobId={} reason={}", jobId, e.getMessage());
    }
  }

  /** Builds a fresh index from Postgres, swaps the alias to it, deletes older indices. */
  public int reindex() throws IOException {
    ElasticsearchClient client = es.getObject();
    String name = PREFIX + "-" + STAMP.format(clock.instant());
    try (InputStream mapping =
        new ClassPathResource("es/job-posts-mapping.json").getInputStream()) {
      client.indices().create(c -> c.index(name).withJson(mapping));
    }
    AtomicInteger count = new AtomicInteger();
    BulkRequest.Builder[] batch = {new BulkRequest.Builder().index(name)};
    AtomicInteger inBatch = new AtomicInteger();
    documents.forEach(
        doc -> {
          batch[0].operations(op -> op.index(i -> i.id(doc.jobId()).document(doc)));
          count.incrementAndGet();
          if (inBatch.incrementAndGet() == BULK_SIZE) {
            flush(client, batch[0]);
            batch[0] = new BulkRequest.Builder().index(name);
            inBatch.set(0);
          }
        });
    if (inBatch.get() > 0) {
      flush(client, batch[0]);
    }
    client.indices().refresh(r -> r.index(name));

    Set<String> old =
        client.indices().exists(e -> e.index(ALIAS)).value()
            ? client.indices().getAlias(a -> a.name(ALIAS)).result().keySet()
            : Set.of();
    client
        .indices()
        .updateAliases(
            u -> {
              old.forEach(o -> u.actions(a -> a.remove(r -> r.index(o).alias(ALIAS))));
              return u.actions(a -> a.add(ad -> ad.index(name).alias(ALIAS)));
            });
    for (String o : old) {
      client.indices().delete(d -> d.index(o));
    }
    ready.set(true);
    log.info("search.reindex index={} docs={} replaced={}", name, count.get(), old);
    return count.get();
  }

  private static void flush(ElasticsearchClient client, BulkRequest.Builder batch) {
    try {
      BulkResponse res = client.bulk(batch.build());
      if (res.errors()) {
        log.warn("search.reindex bulk had item errors");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
