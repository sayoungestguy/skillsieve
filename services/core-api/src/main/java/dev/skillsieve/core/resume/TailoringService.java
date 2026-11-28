package dev.skillsieve.core.resume;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skillsieve.core.common.NotFoundException;
import dev.skillsieve.core.job.JobPost;
import dev.skillsieve.core.job.JobPostRepository;
import dev.skillsieve.core.job.JobSkill;
import dev.skillsieve.core.job.JobSkillRepository;
import dev.skillsieve.core.skill.UserSkill;
import dev.skillsieve.core.skill.UserSkillRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Resume suggestions per job (FR-12). {@link #request} records a PENDING row; {@link
 * TailoringRunner} then calls {@link #run} off the request thread because the local model needs a
 * minute or two. Suggestions are encrypted at rest like the resume.
 */
@Service
public class TailoringService {

  private static final Logger log = LoggerFactory.getLogger(TailoringService.class);
  static final String BUSY = "The suggestion model is busy or off. Try again in a few minutes.";
  static final String INTERRUPTED = "The server restarted while writing these. Try again.";

  private final TailoringRepository tailorings;
  private final ResumeRepository resumes;
  private final ResumeService resumeService;
  private final ResumeCipher cipher;
  private final JobPostRepository jobs;
  private final JobSkillRepository jobSkills;
  private final UserSkillRepository userSkills;
  private final TailorClient client;
  private final TailorProperties props;
  private final ObjectMapper json;
  private final Clock clock;
  private final TransactionTemplate tx;

  public TailoringService(
      TailoringRepository tailorings,
      ResumeRepository resumes,
      ResumeService resumeService,
      ResumeCipher cipher,
      JobPostRepository jobs,
      JobSkillRepository jobSkills,
      UserSkillRepository userSkills,
      TailorClient client,
      TailorProperties props,
      ObjectMapper json,
      Clock clock,
      PlatformTransactionManager txManager) {
    this.tailorings = tailorings;
    this.resumes = resumes;
    this.resumeService = resumeService;
    this.cipher = cipher;
    this.jobs = jobs;
    this.jobSkills = jobSkills;
    this.userSkills = userSkills;
    this.client = client;
    this.props = props;
    this.json = json;
    this.clock = clock;
    this.tx = new TransactionTemplate(txManager);
  }

  /**
   * Starts (or restarts) suggestions for a job. Returns whether the caller must schedule {@link
   * #run}: false when a fresh request for the same resume and post is already pending.
   */
  @Transactional
  public boolean request(UUID userId, UUID jobId) {
    JobPost job = ownJob(userId, jobId);
    if (!client.enabled() || !cipher.enabled()) {
      throw new TailoringConflictException("Resume suggestions are not set up on this server.");
    }
    Resume resume =
        resumes
            .findById(userId)
            .orElseThrow(
                () -> new TailoringConflictException("Save your resume in Settings first."));
    if (jobSkills.findByIdJobPostIdIn(List.of(jobId)).isEmpty()) {
      throw new TailoringConflictException("This job has no skills yet. Extract them first.");
    }
    Tailoring row =
        tailorings
            .findById(new Tailoring.Key(userId, jobId))
            .orElseGet(() -> new Tailoring(userId, jobId));
    Instant now = clock.instant();
    if (row.getStatus() == Tailoring.Status.PENDING
        && !expired(row, now)
        && row.getResumeHmac().equals(resume.getContentHmac())
        && row.getJobTextHash().equals(job.getTextHash())) {
      return false;
    }
    row.start(resume.getContentHmac(), job.getTextHash(), now);
    tailorings.save(row);
    log.info("tailor.requested userId={} jobId={}", userId, jobId);
    return true;
  }

  /** Calls skills-svc and stores the outcome. Never throws: failures are saved on the row. */
  public void run(UUID userId, UUID jobId) {
    TailorClient.TailorRequest body;
    try {
      body = tx.execute(s -> buildRequest(userId, jobId));
      if (body == null) {
        throw new IllegalStateException("no request");
      }
    } catch (RuntimeException e) {
      finish(userId, jobId, null, "Could not prepare the request.");
      log.warn("tailor.prepare_failed jobId={} error={}", jobId, e.getClass().getSimpleName());
      return;
    }
    long start = System.nanoTime();
    try {
      TailorResultDto result = client.tailor(body);
      finish(userId, jobId, result, null);
      log.info(
          "tailor.done jobId={} suggestions={} seconds={}",
          jobId,
          result.suggestions().size(),
          (System.nanoTime() - start) / 1_000_000_000);
    } catch (HttpStatusCodeException e) {
      String why =
          e.getStatusCode().value() == 422
              ? "Your resume has no bullet points to rewrite. Use one line per achievement."
              : BUSY;
      finish(userId, jobId, null, why);
      log.warn("tailor.failed jobId={} status={}", jobId, e.getStatusCode().value());
    } catch (ResourceAccessException e) {
      finish(userId, jobId, null, BUSY);
      log.warn("tailor.failed jobId={} error=timeout_or_unreachable", jobId);
    } catch (RuntimeException e) {
      finish(userId, jobId, null, "Something went wrong. Try again.");
      log.warn("tailor.failed jobId={} error={}", jobId, e.getClass().getSimpleName());
    }
  }

  /**
   * Fails every PENDING row. Runs happen in this process only (one core-api replica), so after a
   * restart nothing will finish them; without this they would block a retry until they expire.
   */
  @Transactional
  public int failInterrupted() {
    int n = tailorings.failAllPending(INTERRUPTED, clock.instant());
    if (n > 0) {
      log.info("tailor.interrupted count={}", n);
    }
    return n;
  }

  /** Marks a request failed when it could not be queued. */
  @Transactional
  public void reject(UUID userId, UUID jobId) {
    tailorings
        .findById(new Tailoring.Key(userId, jobId))
        .ifPresent(t -> t.fail(BUSY, clock.instant()));
  }

  @Transactional(readOnly = true)
  public TailoringDto get(UUID userId, UUID jobId) {
    JobPost job = ownJob(userId, jobId);
    Tailoring row =
        tailorings
            .findById(new Tailoring.Key(userId, jobId))
            .orElseThrow(() -> new NotFoundException("Suggestions"));
    String currentHmac = resumes.findById(userId).map(Resume::getContentHmac).orElse("");
    boolean stale =
        !row.getResumeHmac().equals(currentHmac) || !row.getJobTextHash().equals(job.getTextHash());
    Instant now = clock.instant();
    if (row.getStatus() == Tailoring.Status.PENDING && expired(row, now)) {
      return dto(row, Tailoring.Status.FAILED, stale, null, "This took too long. Try again.");
    }
    TailorResultDto result = null;
    byte[] blob = row.getCiphertext();
    if (row.getStatus() == Tailoring.Status.DONE && blob != null) {
      result = read(cipher.decrypt(userId, blob));
    }
    return dto(row, row.getStatus(), stale, result, row.getError());
  }

  // Called inside tx (self-invocation would skip @Transactional).
  TailorClient.TailorRequest buildRequest(UUID userId, UUID jobId) {
    JobPost job = ownJob(userId, jobId);
    String resumeText =
        resumeService
            .find(userId)
            .orElseThrow(() -> new TailoringConflictException("resume deleted"))
            .text();
    Map<Integer, Integer> levels =
        userSkills.findByIdUserId(userId).stream()
            .collect(Collectors.toMap(UserSkill::getSkillId, UserSkill::getLevel));
    List<JobSkill> skills = jobSkills.findByIdJobPostIdIn(List.of(jobId));
    return new TailorClient.TailorRequest(
        resumeText,
        job.getTitle(),
        job.getCompany(),
        skills.stream()
            .map(s -> new TailorClient.JobSkill(s.getSkill().getName(), s.getImportance().name()))
            .toList(),
        skills.stream()
            .filter(s -> levels.getOrDefault(s.getSkill().getId(), 0) == 0)
            .map(s -> s.getSkill().getName())
            .toList());
  }

  private void finish(UUID userId, UUID jobId, TailorResultDto result, String error) {
    tx.executeWithoutResult(s -> saveOutcome(userId, jobId, result, error));
  }

  private void saveOutcome(UUID userId, UUID jobId, TailorResultDto result, String error) {
    tailorings
        .findById(new Tailoring.Key(userId, jobId))
        .ifPresent(
            row -> {
              if (result != null) {
                row.succeed(cipher.encrypt(userId, write(result)), clock.instant());
              } else {
                row.fail(error, clock.instant());
              }
            });
  }

  private JobPost ownJob(UUID userId, UUID jobId) {
    return jobs.findByIdAndUserId(jobId, userId).orElseThrow(() -> new NotFoundException("Job"));
  }

  private boolean expired(Tailoring row, Instant now) {
    return row.getRequestedAt().plus(props.expireAfter()).isBefore(now);
  }

  private static TailoringDto dto(
      Tailoring row, Tailoring.Status status, boolean stale, TailorResultDto result, String error) {
    return new TailoringDto(
        status,
        stale,
        result == null ? null : result.model(),
        result == null ? List.of() : result.suggestions(),
        status == Tailoring.Status.FAILED ? error : null,
        row.getRequestedAt(),
        row.getFinishedAt());
  }

  private String write(TailorResultDto result) {
    try {
      return json.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private TailorResultDto read(String text) {
    try {
      return json.readValue(text.getBytes(StandardCharsets.UTF_8), TailorResultDto.class);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("stored suggestions unreadable", e);
    }
  }
}
