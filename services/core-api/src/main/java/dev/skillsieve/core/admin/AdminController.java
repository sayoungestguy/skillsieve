package dev.skillsieve.core.admin;

import dev.skillsieve.core.search.JobIndex;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OWNER-only maintenance (SecurityConfig restricts /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final JobIndex index;

  public AdminController(JobIndex index) {
    this.index = index;
  }

  /** Rebuilds the search index from Postgres into a new index and swaps the alias. */
  @PostMapping("/reindex")
  ResponseEntity<Map<String, Object>> reindex() throws IOException {
    if (!index.enabled()) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("detail", "Elasticsearch is not configured (ES_URL)."));
    }
    return ResponseEntity.accepted().body(Map.of("indexed", index.reindex()));
  }
}
