package dev.skillsieve.core.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResumeCipherTest {

  private static final String KEY =
      Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
  private final ResumeCipher cipher = new ResumeCipher(new ResumeProperties(KEY));
  private final UUID user = UUID.randomUUID();

  @Test
  void shouldRoundTripAndNotContainPlainText() {
    byte[] blob = cipher.encrypt(user, "Built Kafka pipelines at Acme");

    assertThat(new String(blob, StandardCharsets.UTF_8)).doesNotContain("Kafka");
    assertThat(cipher.decrypt(user, blob)).isEqualTo("Built Kafka pipelines at Acme");
  }

  @Test
  void shouldUseFreshNonceEachTime() {
    assertThat(cipher.encrypt(user, "same")).isNotEqualTo(cipher.encrypt(user, "same"));
  }

  @Test
  void shouldRejectCiphertextMovedToAnotherUser() {
    byte[] blob = cipher.encrypt(user, "secret");

    assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), blob))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectTamperedCiphertext() {
    byte[] blob = cipher.encrypt(user, "secret");
    blob[blob.length - 1] ^= 1;

    assertThatThrownBy(() -> cipher.decrypt(user, blob)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldGiveStableKeyedFingerprint() {
    ResumeCipher other =
        new ResumeCipher(
            new ResumeProperties(
                Base64.getEncoder()
                    .encodeToString(
                        "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8))));

    assertThat(cipher.fingerprint("cv")).hasSize(64).isEqualTo(cipher.fingerprint("cv"));
    assertThat(other.fingerprint("cv")).isNotEqualTo(cipher.fingerprint("cv"));
  }

  @Test
  void shouldBeOffWithoutKeyAndRejectBadKeys() {
    ResumeCipher off = new ResumeCipher(new ResumeProperties(""));

    assertThat(off.enabled()).isFalse();
    assertThatThrownBy(() -> off.encrypt(user, "x")).isInstanceOf(ResumeDisabledException.class);
    assertThatThrownBy(() -> new ResumeCipher(new ResumeProperties("c2hvcnQ=")))
        .hasMessageContaining("32 bytes");
    assertThatThrownBy(() -> new ResumeCipher(new ResumeProperties("not base64!")))
        .hasMessageContaining("base64");
  }
}
