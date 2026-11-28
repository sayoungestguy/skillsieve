package dev.skillsieve.core.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resume")
public class Resume {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false)
  private byte[] ciphertext;

  @Column(name = "content_hmac", nullable = false)
  private String contentHmac;

  @Column(name = "char_count", nullable = false)
  private int charCount;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Resume() {}

  Resume(UUID userId) {
    this.userId = userId;
  }

  void replace(byte[] ciphertext, String contentHmac, int charCount, Instant now) {
    this.ciphertext = ciphertext.clone();
    this.contentHmac = contentHmac;
    this.charCount = charCount;
    this.updatedAt = now;
  }

  public UUID getUserId() {
    return userId;
  }

  byte[] getCiphertext() {
    return ciphertext.clone();
  }

  public String getContentHmac() {
    return contentHmac;
  }

  public int getCharCount() {
    return charCount;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
