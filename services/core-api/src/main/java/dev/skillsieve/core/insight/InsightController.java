package dev.skillsieve.core.insight;

import dev.skillsieve.core.application.Status;
import dev.skillsieve.core.common.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

  private final InsightService service;

  public InsightController(InsightService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  InsightSummaryDto summary(Authentication auth) {
    return service.summary(CurrentUser.id(auth));
  }

  /** {@code from}/{@code to} are dates (UTC days); {@code to} is inclusive. */
  @GetMapping("/skills")
  List<SkillInsightDto> skills(
      @RequestParam(required = false) @Nullable Status status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable
          LocalDate to,
      @RequestParam(defaultValue = "20") int limit,
      Authentication auth) {
    return service.skills(CurrentUser.id(auth), status, startOf(from), startOfNext(to), limit);
  }

  @Nullable
  private static Instant startOf(@Nullable LocalDate d) {
    return d == null ? null : d.atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  @Nullable
  private static Instant startOfNext(@Nullable LocalDate d) {
    return d == null ? null : startOf(d.plusDays(1));
  }
}
