package dev.skillsieve.core.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusEventRepository extends JpaRepository<StatusEvent, Long> {

  List<StatusEvent> findByApplicationIdOrderByChangedAtDescIdDesc(UUID applicationId);
}
