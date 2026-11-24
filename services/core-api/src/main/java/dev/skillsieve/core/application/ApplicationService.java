package dev.skillsieve.core.application;

import dev.skillsieve.core.application.ApplicationPatch.Field;
import dev.skillsieve.core.common.NotFoundException;
import dev.skillsieve.core.common.StaleVersionException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

  private final ApplicationRepository applications;
  private final StatusEventRepository events;
  private final Clock clock;

  public ApplicationService(
      ApplicationRepository applications, StatusEventRepository events, Clock clock) {
    this.applications = applications;
    this.events = events;
    this.clock = clock;
  }

  /** Applies a PATCH with optimistic locking; writes a status_event when the status changes. */
  @Transactional
  public ApplicationDto update(UUID userId, UUID applicationId, ApplicationPatch patch) {
    Application app = owned(userId, applicationId);
    if (patch.getVersion() != app.getVersion()) {
      throw new StaleVersionException();
    }
    Instant now = clock.instant();
    Status before = app.getStatus();
    if (patch.has(Field.STATUS)
        && patch.getStatus() != null
        && app.moveTo(patch.getStatus(), now)) {
      events.save(new StatusEvent(app.getId(), before, app.getStatus(), now));
      log.info("application.status id={} from={} to={}", app.getId(), before, app.getStatus());
    }
    if (patch.has(Field.NEXT_ACTION_AT)) {
      app.setNextActionAt(patch.getNextActionAt(), now);
    }
    if (patch.has(Field.NOTES)) {
      app.setNotes(patch.getNotes(), now);
    }
    if (patch.has(Field.POSITION) && patch.getPosition() != null) {
      app.setPosition(patch.getPosition(), now);
    }
    return ApplicationDto.from(applications.saveAndFlush(app));
  }

  /** Newest first. */
  @Transactional(readOnly = true)
  public List<StatusEventDto> history(UUID userId, UUID applicationId) {
    Application app = owned(userId, applicationId);
    return events.findByApplicationIdOrderByChangedAtDescIdDesc(app.getId()).stream()
        .map(StatusEventDto::from)
        .toList();
  }

  private Application owned(UUID userId, UUID applicationId) {
    return applications
        .findOwned(applicationId, userId)
        .orElseThrow(() -> new NotFoundException("Application"));
  }
}
