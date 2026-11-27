package dev.skillsieve.core.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.skillsieve.core.auth.AuthCookie;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.util.WebUtils;

/**
 * Stateless JWT-in-cookie auth (ARCHITECTURE §7). DEMO users may only read. CSRF protection is
 * SameSite=Strict plus a required X-Requested-With header ({@link ApiRequestGuardFilter}).
 */
@Configuration
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int MIN_SECRET_BYTES = 32;
  private static final int BCRYPT_COST = 12;
  private static final String PROBLEM_401 =
      "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Sign in first.\"}";
  private static final String PROBLEM_403 =
      "{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"You cannot do this.\"}";

  @Bean
  SecurityFilterChain api(HttpSecurity http, AppProperties props) throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(b -> b.disable())
        .formLogin(f -> f.disable())
        .addFilterBefore(new ApiRequestGuardFilter(), UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout")
                    .permitAll()
                    .requestMatchers("/api/health", "/livez", "/readyz", "/v3/api-docs")
                    .permitAll()
                    // Management port (8081) only: not routed by the Service or Ingress.
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("OWNER")
                    .requestMatchers(HttpMethod.GET, "/api/**")
                    .authenticated()
                    .requestMatchers("/api/**")
                    .hasAnyRole("OWNER", "USER")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            o ->
                o.bearerTokenResolver(cookieTokenResolver())
                    .jwt(j -> j.jwtAuthenticationConverter(roleConverter()))
                    .authenticationEntryPoint((req, res, e) -> writeProblem(res, 401, PROBLEM_401)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, ex) -> writeProblem(res, 401, PROBLEM_401))
                    .accessDeniedHandler((req, res, ex) -> writeProblem(res, 403, PROBLEM_403)));
    return http.build();
  }

  @Bean
  SecretKey jwtKey(AppProperties props) {
    String secret = props.jwtSecret();
    if (secret.isBlank() && props.allowRandomJwtSecret()) {
      byte[] random = new byte[64];
      RANDOM.nextBytes(random);
      log.warn("auth.jwt random secret generated; tokens end when the app restarts (dev only)");
      return new SecretKeySpec(random, "HmacSHA256");
    }
    byte[] bytes = secret.isBlank() ? new byte[0] : Base64.getDecoder().decode(secret);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException("JWT_SECRET must be base64 of at least 32 random bytes");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey key) {
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(BCRYPT_COST);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  private static BearerTokenResolver cookieTokenResolver() {
    return request -> {
      var cookie = WebUtils.getCookie(request, AuthCookie.NAME);
      return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    };
  }

  private static JwtAuthenticationConverter roleConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString("role");
          return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
    return converter;
  }

  private static void writeProblem(HttpServletResponse res, int status, String body)
      throws java.io.IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.getWriter().write(body);
  }
}
