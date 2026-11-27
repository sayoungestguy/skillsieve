package dev.skillsieve.core.job;

import dev.skillsieve.core.application.Application;
import dev.skillsieve.core.application.ApplicationRepository;
import dev.skillsieve.core.application.Status;
import dev.skillsieve.core.application.StatusEvent;
import dev.skillsieve.core.application.StatusEventRepository;
import dev.skillsieve.core.common.NotFoundException;
import dev.skillsieve.core.common.PageDto;
import dev.skillsieve.core.common.TextNormaliser;
import dev.skillsieve.core.config.AppProperties;
import dev.skillsieve.core.messaging.OutboxRepository;
import dev.skillsieve.core.search.JobChangedEvent;
import dev.skillsieve.core.skill.UserSkill;
import dev.skillsieve.core.skill.UserSkillRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

  private static final Logger log = LoggerFactory.getLogger(JobService.class);
  static final int MAX_PAGE_SIZE = 200;

  private final JobPostRepository jobs;
  private final ApplicationRepository applications;
  private final StatusEventRepository events;
  private final JobSkillRepository jobSkills;
  private final UserSkillRepository userSkills;
  private final ApplicationEventPublisher changes;
  private final OutboxRepository outbox;
  private final boolean asyncExtraction;
  private final Counter jobsSaved;
  private final Clock clock;

  public JobService(
      JobPostRepository jobs,
      ApplicationRepository applications,
      StatusEventRepository events,
      JobSkillRepository jobSkills,
      UserSkillRepository userSkills,
      ApplicationEventPublisher changes,
      OutboxRepository outbox,
      AppProperties props,
      MeterRegistry meters,
      Clock clock) {
    this.jobs = jobs;
    this.applications = applications;
    this.events = events;
    this.jobSkills = jobSkills;
    this.userSkills = userSkills;
    this.changes = changes;
    this.outbox = outbox;
    this.asyncExtraction = props.asyncExtraction();
    this.jobsSaved =
        Counter.builder("skillsieve.jobs.saved")
            .description("Job posts saved (web or extension)")
            .register(meters);
    this.clock = clock;
  }

  /** Saves a post with a SAVED application. Duplicate text (after normalising) → 409. */
  @Transactional
  public JobDto create(UUID userId, CreateJobRequest req) {
    String hash = TextNormaliser.hashOf(req.rawText());
    jobs.findByUserIdAndTextHash(userId, hash)
        .ifPresent(
            existing -> {
              throw new DuplicateJobException(existing.getId());
            });
    Instant now = clock.instant();
    JobPost job = jobs.save(new JobPost(userId, req.rawText(), hash, req.sourceUrl(), now));
    Application app = applications.save(new Application(job, now));
    events.save(new StatusEvent(app.getId(), null, Status.SAVED, now));
    changes.publishEvent(JobChangedEvent.changed(job.getId()));
    if (asyncExtraction) {
      outbox.appendJobCreated(job.getId(), hash, now); // same transaction as the job (outbox)
    }
    jobsSaved.increment();
    log.info("job.saved jobId={}", job.getId());
    return JobMapper.toDto(app, List.of(), Map.of());
  }

  @Transactional(readOnly = true)
  public PageDto<JobSummaryDto> board(
      UUID userId, @Nullable Status status, @Nullable String q, int page, int size) {
    int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
    String like =
        q == null || q.isBlank()
            ? null
            : "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
    Page<Application> result =
        applications.findBoard(userId, status, like, PageRequest.of(Math.max(page, 0), pageSize));
    Map<UUID, List<JobSkill>> skills =
        skillsByJob(result.stream().map(a -> a.getJobPost().getId()).toList());
    List<JobSummaryDto> content =
        result.stream()
            .map(
                a -> JobMapper.toSummary(a, skills.getOrDefault(a.getJobPost().getId(), List.of())))
            .toList();
    return new PageDto<>(content, result.getTotalElements(), result.getNumber(), pageSize);
  }

  @Transactional(readOnly = true)
  public JobDto get(UUID userId, UUID jobId) {
    return toDto(userId, owned(userId, jobId));
  }

  @Transactional
  public JobDto update(UUID userId, UUID jobId, UpdateJobRequest req) {
    JobPost job = owned(userId, jobId);
    job.edit(req, clock.instant());
    changes.publishEvent(JobChangedEvent.changed(jobId));
    return toDto(userId, job);
  }

  @Transactional
  public void delete(UUID userId, UUID jobId) {
    jobs.delete(owned(userId, jobId));
    changes.publishEvent(JobChangedEvent.deleted(jobId));
    log.info("job.deleted jobId={}", jobId);
  }

  /** Marks the job for (re-)extraction; in async mode also queues it for skills-svc. */
  @Transactional
  public void requestExtraction(UUID userId, UUID jobId) {
    JobPost job = owned(userId, jobId);
    Instant now = clock.instant();
    job.requestExtraction(now);
    if (asyncExtraction) {
      outbox.appendJobCreated(jobId, job.getTextHash(), now);
    }
    log.info("job.extraction.requested jobId={}", jobId);
  }

  private JobPost owned(UUID userId, UUID jobId) {
    return jobs.findByIdAndUserId(jobId, userId).orElseThrow(() -> new NotFoundException("Job"));
  }

  private JobDto toDto(UUID userId, JobPost job) {
    Application app =
        applications
            .findByJobPostId(job.getId())
            .orElseThrow(() -> new IllegalStateException("job without application"));
    Map<Integer, Integer> levels =
        userSkills.findByIdUserId(userId).stream()
            .collect(Collectors.toMap(UserSkill::getSkillId, UserSkill::getLevel));
    return JobMapper.toDto(
        app, skillsByJob(List.of(job.getId())).getOrDefault(job.getId(), List.of()), levels);
  }

  private Map<UUID, List<JobSkill>> skillsByJob(List<UUID> jobIds) {
    if (jobIds.isEmpty()) {
      return Map.of();
    }
    return jobSkills.findByIdJobPostIdIn(jobIds).stream()
        .collect(Collectors.groupingBy(JobSkill::getJobPostId));
  }

  private static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /** True when skills-svc is reached through the outbox instead of during the request. */
  public boolean asyncExtraction() {
    return asyncExtraction;
  }
}
