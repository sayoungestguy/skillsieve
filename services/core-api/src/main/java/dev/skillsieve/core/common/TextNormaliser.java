package dev.skillsieve.core.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises job text before hashing (TECHNICAL_DESIGN §3): NFKC, lower-case, collapse whitespace,
 * trim. Must stay identical to the Python version; both are tested against
 * data/test-vectors/normalise.json.
 */
public final class TextNormaliser {

  private static final Pattern WHITESPACE =
      Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  private TextNormaliser() {}

  public static String normalise(String text) {
    String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    return WHITESPACE.matcher(nfkc).replaceAll(" ").strip();
  }

  public static String sha256Hex(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JVM", e);
    }
  }

  public static String hashOf(String rawText) {
    return sha256Hex(normalise(rawText));
  }
}
