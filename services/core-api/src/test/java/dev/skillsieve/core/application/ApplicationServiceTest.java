package dev.skillsieve.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.skillsieve.core.common.NotFoundException;
import dev.skillsieve.core.common.StaleVersionException;
import dev.skillsieve.core.job.JobPost;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-24T02:00:00Z");
  private static final UUID USER = UUID.randomUUID();

  @Mock private ApplicationRepository applications;
  @Mock private StatusEventRepository events;

  private ApplicationService service;
  private Application app;

  @BeforeEach
  void setUp() {
    service =
        new ApplicationService(applications, events, e -> {}, Clock.fixed(NOW, ZoneOffset.UTC));
    JobPost job = new JobPost(USER, "text", "hash", null, NOW);
    app = new Application(job, NOW);
  }

  @Test
  void shouldThrowStaleVersionWhenVersionDiffers() {
    when(applications.findOwned(app.getId(), USER)).thenReturn(Optional.of(app));
    ApplicationPatch patch = patch(Status.APPLIED, 7);

    assertThatThrownBy(() -> service.update(USER, app.getId(), patch))
        .isInstanceOf(StaleVersionException.class);
    verify(events, never()).save(any());
  }

  @Test
  void shouldRecordEventAndAppliedAtWhenMovingToApplied() {
    when(applications.findOwned(app.getId(), USER)).thenReturn(Optional.of(app));
    when(applications.saveAndFlush(app)).thenReturn(app);

    ApplicationDto dto = service.update(USER, app.getId(), patch(Status.APPLIED, 0));

    assertThat(dto.status()).isEqualTo(Status.APPLIED);
    assertThat(dto.appliedAt()).isEqualTo(NOW);
    verify(events).save(any(StatusEvent.class));
  }

  @Test
  void shouldNotRecordEventWhenStatusIsUnchanged() {
    when(applications.findOwned(app.getId(), USER)).thenReturn(Optional.of(app));
    when(applications.saveAndFlush(app)).thenReturn(app);

    service.update(USER, app.getId(), patch(Status.SAVED, 0));

    verify(events, never()).save(any());
  }

  @Test
  void shouldReturn404ForAnotherUsersApplication() {
    UUID other = UUID.randomUUID();
    when(applications.findOwned(app.getId(), other)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.history(other, app.getId()))
        .isInstanceOf(NotFoundException.class);
  }

  private static ApplicationPatch patch(Status status, int version) {
    ApplicationPatch p = new ApplicationPatch();
    p.setStatus(status);
    p.setVersion(version);
    return p;
  }
}
