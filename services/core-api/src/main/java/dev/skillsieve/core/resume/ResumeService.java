package dev.skillsieve.core.resume;

import dev.skillsieve.core.common.NotFoundException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores each user's resume encrypted. The plain text is never logged and never leaves core-api
 * except to its owner (and, in part 3, to the local Ollama model; never to Gemini, CLAUDE.md C5).
 */
@Service
public class ResumeService {

  private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
  static final int MAX_CHARS = 20_000;

  private final ResumeRepository resumes;
  private final ResumeCipher cipher;
  private final Clock clock;

  public ResumeService(ResumeRepository resumes, ResumeCipher cipher, Clock clock) {
    this.resumes = resumes;
    this.cipher = cipher;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ResumeDto get(UUID userId) {
    return find(userId).orElseThrow(() -> new NotFoundException("Resume"));
  }

  /** Decrypted resume text, for server-side use (tailoring). */
  @Transactional(readOnly = true)
  public Optional<ResumeDto> find(UUID userId) {
    requireEnabled();
    return resumes
        .findById(userId)
        .map(
            r ->
                new ResumeDto(
                    cipher.decrypt(userId, r.getCiphertext()), r.getCharCount(), r.getUpdatedAt()));
  }

  @Transactional
  public ResumeDto save(UUID userId, String text) {
    requireEnabled();
    String clean = text.strip();
    Resume resume = resumes.findById(userId).orElseGet(() -> new Resume(userId));
    resume.replace(
        cipher.encrypt(userId, clean), cipher.fingerprint(clean), clean.length(), clock.instant());
    resumes.save(resume);
    log.info("resume.saved userId={} chars={}", userId, clean.length());
    return new ResumeDto(clean, clean.length(), resume.getUpdatedAt());
  }

  @Transactional
  public void delete(UUID userId) {
    requireEnabled();
    resumes.deleteById(userId);
    log.info("resume.deleted userId={}", userId);
  }

  private void requireEnabled() {
    if (!cipher.enabled()) {
      throw new ResumeDisabledException();
    }
  }
}
