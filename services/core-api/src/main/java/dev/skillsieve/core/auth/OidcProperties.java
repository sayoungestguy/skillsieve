package dev.skillsieve.core.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Keycloak sign-in (ADR-009), bound from {@code skillsieve.oidc.*}.
 *
 * @param issuer public realm URL, e.g. https://host/auth/realms/skillsieve (OIDC_ISSUER); empty =
 *     Keycloak sign-in off. Must equal the {@code iss} claim and is where browsers are sent.
 * @param internalUrl the same realm reached from inside the cluster (OIDC_INTERNAL_URL), for the
 *     token and key endpoints; empty = use {@code issuer}
 * @param clientId OIDC client (OIDC_CLIENT_ID)
 * @param clientSecret confidential client secret (OIDC_CLIENT_SECRET)
 * @param appUrl public URL of this app, for the callback and post-logout redirects (APP_URL)
 */
@ConfigurationProperties("skillsieve.oidc")
public record OidcProperties(
    @DefaultValue("") String issuer,
    @DefaultValue("") String internalUrl,
    @DefaultValue("skillsieve-web") String clientId,
    @DefaultValue("") String clientSecret,
    @DefaultValue("") String appUrl) {

  public boolean enabled() {
    return !issuer.isBlank() && !clientSecret.isBlank() && !appUrl.isBlank();
  }

  String publicBase() {
    return strip(issuer);
  }

  String backchannelBase() {
    return internalUrl.isBlank() ? strip(issuer) : strip(internalUrl);
  }

  String redirectUri() {
    return strip(appUrl) + "/api/auth/oidc/callback";
  }

  String loginPage() {
    return strip(appUrl) + "/login";
  }

  private static String strip(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
