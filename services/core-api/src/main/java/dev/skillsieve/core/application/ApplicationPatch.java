package dev.skillsieve.core.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * PATCH body for an application. Tracks which fields were sent, so {@code "nextActionAt": null}
 * clears the date while a missing field leaves it alone.
 */
public final class ApplicationPatch {

  /** Fields a client may send. */
  enum Field {
    STATUS,
    NEXT_ACTION_AT,
    NOTES,
    POSITION
  }

  private final Set<Field> present = EnumSet.noneOf(Field.class);

  @Nullable private Status status;
  @Nullable private LocalDate nextActionAt;

  @Nullable
  @Size(max = 5000)
  private String notes;

  @Nullable
  @Min(0)
  @Max(100_000)
  private Integer position;

  @NotNull private Integer version;

  boolean has(Field field) {
    return present.contains(field);
  }

  @Nullable
  public Status getStatus() {
    return status;
  }

  public void setStatus(@Nullable Status status) {
    present.add(Field.STATUS);
    this.status = status;
  }

  @Nullable
  public LocalDate getNextActionAt() {
    return nextActionAt;
  }

  public void setNextActionAt(@Nullable LocalDate nextActionAt) {
    present.add(Field.NEXT_ACTION_AT);
    this.nextActionAt = nextActionAt;
  }

  @Nullable
  public String getNotes() {
    return notes;
  }

  public void setNotes(@Nullable String notes) {
    present.add(Field.NOTES);
    this.notes = notes;
  }

  @Nullable
  public Integer getPosition() {
    return position;
  }

  public void setPosition(@Nullable Integer position) {
    present.add(Field.POSITION);
    this.position = position;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
