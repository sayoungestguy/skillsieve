package dev.skillsieve.core.resume;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Runs tailoring requests one at a time in the background: the local model answers one prompt at a
 * time anyway (OLLAMA_NUM_PARALLEL=1). A full queue fails the request with "try again".
 */
@Component
public class TailoringRunner {

  static final int QUEUE = 20;

  private final TailoringService service;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(QUEUE),
          r -> {
            Thread t = new Thread(r, "tailoring");
            t.setDaemon(true);
            return t;
          });

  public TailoringRunner(TailoringService service) {
    this.service = service;
  }

  public void submit(UUID userId, UUID jobId) {
    try {
      executor.execute(() -> service.run(userId, jobId));
    } catch (RejectedExecutionException e) {
      service.reject(userId, jobId);
    }
  }

  @PreDestroy
  void stop() {
    executor.shutdownNow();
  }
}
