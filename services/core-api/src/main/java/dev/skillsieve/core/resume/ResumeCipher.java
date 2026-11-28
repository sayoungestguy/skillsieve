package dev.skillsieve.core.resume;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for resume text, JDK only. The user id is the associated data, so a ciphertext copied
 * to another user's row fails to decrypt. Layout: version byte, 12-byte nonce, ciphertext + tag.
 */
@Component
public class ResumeCipher {

  private static final byte VERSION = 1;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int KEY_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  @Nullable private final SecretKeySpec aesKey;
  @Nullable private final SecretKeySpec hmacKey;

  public ResumeCipher(ResumeProperties props) {
    if (props.key().isBlank()) {
      aesKey = null;
      hmacKey = null;
      return;
    }
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(props.key().strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("RESUME_KEY must be base64", e);
    }
    if (raw.length != KEY_BYTES) {
      throw new IllegalStateException("RESUME_KEY must be 32 bytes (openssl rand -base64 32)");
    }
    aesKey = new SecretKeySpec(raw, "AES");
    // Separate key for the HMAC, derived from the same secret.
    hmacKey =
        new SecretKeySpec(hmac(new SecretKeySpec(raw, "HmacSHA256"), "resume-hmac"), "HmacSHA256");
  }

  public boolean enabled() {
    return aesKey != null;
  }

  public byte[] encrypt(UUID userId, String text) {
    byte[] nonce = new byte[NONCE_BYTES];
    RANDOM.nextBytes(nonce);
    try {
      Cipher c = cipher(Cipher.ENCRYPT_MODE, nonce, userId);
      byte[] ct = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(1 + NONCE_BYTES + ct.length)
          .put(VERSION)
          .put(nonce)
          .put(ct)
          .array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("resume encryption failed", e);
    }
  }

  public String decrypt(UUID userId, byte[] blob) {
    if (blob.length < 1 + NONCE_BYTES || blob[0] != VERSION) {
      throw new IllegalStateException("unknown resume ciphertext format");
    }
    ByteBuffer buf = ByteBuffer.wrap(blob, 1, blob.length - 1);
    byte[] nonce = new byte[NONCE_BYTES];
    buf.get(nonce);
    byte[] ct = new byte[buf.remaining()];
    buf.get(ct);
    try {
      return new String(
          cipher(Cipher.DECRYPT_MODE, nonce, userId).doFinal(ct), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("resume decryption failed (wrong key or tampered row)", e);
    }
  }

  /** Hex HMAC-SHA256 of the text: a stable cache key that does not reveal the text. */
  public String fingerprint(String text) {
    return HexFormat.of().formatHex(hmac(requireKey(hmacKey), text));
  }

  private Cipher cipher(int mode, byte[] nonce, UUID userId) throws GeneralSecurityException {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(mode, requireKey(aesKey), new GCMParameterSpec(TAG_BITS, nonce));
    c.updateAAD(userId.toString().getBytes(StandardCharsets.UTF_8));
    return c;
  }

  private static SecretKeySpec requireKey(@Nullable SecretKeySpec key) {
    if (key == null) {
      throw new ResumeDisabledException();
    }
    return key;
  }

  private static byte[] hmac(SecretKeySpec key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC failed", e);
    }
  }
}
