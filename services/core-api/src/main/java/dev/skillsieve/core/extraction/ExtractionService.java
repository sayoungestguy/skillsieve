package dev.skillsieve.core.extraction;

import dev.skillsieve.core.job.ExtractionStatus;
import dev.skillsieve.core.job.JobPost;
import dev.skillsieve.core.job.JobPostRepository;
import dev.skillsieve.core.job.JobSkill;
import dev.skillsieve.core.job.JobSkillRepository;
import dev.skillsieve.core.search.JobChangedEvent;
import dev.skillsieve.core.skill.Skill;
import dev.skillsieve.core.skill.SkillRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Synchronous extraction for R1 (ADR-006): call skills-svc outside any DB transaction, then store
 * the result in a short one. Failures never fail the save; the job stays PENDING (try again later)
 * or becomes FAILED (the post itself was rejected).
 */
@Service
public class ExtractionService {

  private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);
  static final String RETRY_LATER = "Skill extraction is busy or offline. Try again later.";
  static final String REJECTED = "Skill extraction could not read this post.";

  private final SkillsClient client;
  private final JobPostRepository jobs;
  private final JobSkillRepository jobSkills;
  private final SkillRepository skills;
  private final TransactionTemplate tx;
  private final ApplicationEventPublisher changes;
  private final Clock clock;

  public ExtractionService(
      SkillsClient client,
      JobPostRepository jobs,
      JobSkillRepository jobSkills,
      SkillRepository skills,
      TransactionTemplate tx,
      ApplicationEventPublisher changes,
      Clock clock) {
    this.client = client;
    this.jobs = jobs;
    this.jobSkills = jobSkills;
    this.skills = skills;
    this.tx = tx;
    this.changes = changes;
    this.clock = clock;
  }

  /** Must be called after the job is committed. Never throws for skills-svc problems. */
  public void extract(UUID jobId) {
    Optional<JobPost> job = jobs.findById(jobId);
    if (job.isEmpty()) {
      return;
    }
    Optional<ExtractionResultDto> result;
    try {
      result = client.extract(job.get().getTextHash(), job.get().getRawText());
    } catch (RestClientResponseException e) {
      boolean later =
          e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
              || e.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)
              || e.getStatusCode().isSameCodeAs(HttpStatus.GATEWAY_TIMEOUT);
      log.warn("extraction.error jobId={} status={}", jobId, e.getStatusCode().value());
      markProblem(
          jobId,
          later ? ExtractionStatus.PENDING : ExtractionStatus.FAILED,
          later ? RETRY_LATER : REJECTED);
      return;
    } catch (ResourceAccessException e) {
      log.warn("extraction.unreachable jobId={} reason={}", jobId, e.getClass().getSimpleName());
      markProblem(jobId, ExtractionStatus.PENDING, RETRY_LATER);
      return;
    } catch (RestClientException e) {
      log.warn("extraction.bad_response jobId={}", jobId);
      markProblem(jobId, ExtractionStatus.FAILED, REJECTED);
      return;
    }
    result.ifPresent(r -> apply(jobId, r));
  }

  private void apply(UUID jobId, ExtractionResultDto r) {
    tx.executeWithoutResult(
        status ->
            jobs.findById(jobId)
                .ifPresent(
                    job -> {
                      Map<String, Skill> bySlug =
                          skills
                              .findBySlugIn(
                                  r.skills().stream().map(ExtractionResultDto.Skill::slug).toList())
                              .stream()
                              .collect(Collectors.toMap(Skill::getSlug, Function.identity()));
                      jobSkills.deleteForJob(jobId);
                      List<JobSkill> rows =
                          r.skills().stream()
                              .filter(s -> bySlug.containsKey(s.slug()))
                              .map(
                                  s ->
                                      new JobSkill(
                                          jobId,
                                          bySlug.get(s.slug()),
                                          s.importance(),
                                          s.evidence()))
                              .toList();
                      jobSkills.saveAll(rows);
                      JobPost fresh = jobs.findById(jobId).orElse(job);
                      fresh.applyExtraction(
                          r.company(),
                          r.title(),
                          r.location(),
                          r.seniority(),
                          r.minYears(),
                          clock.instant());
                      changes.publishEvent(JobChangedEvent.changed(jobId));
                      log.info(
                          "extraction.done jobId={} skills={} unmapped={} cached={}",
                          jobId,
                          rows.size(),
                          r.unmapped().size(),
                          r.cached());
                    }));
  }

  private void markProblem(UUID jobId, ExtractionStatus status, String error) {
    tx.executeWithoutResult(
        s ->
            jobs.findById(jobId)
                .ifPresent(job -> job.markExtractionProblem(status, error, clock.instant())));
  }
}
