package br.com.fiap.alertabairro.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /** Cost 10, same as the previous back end, so existing hashes still verify. */
  private static final int BCRYPT_COST = 10;

  private static final int MIN_SECRET_LENGTH = 32;

  private static final String[] PUBLIC_PATHS = {
      "/auth/**", "/actuator/health", "/actuator/health/**",
      "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
  };

  private final SecretKey key;

  public SecurityConfig(@Value("${alertabairro.jwt.segredo:${JWT_SECRET:}}") String secret) {
    if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
      // Fail on startup, not on the first request: a container that refuses to boot is
      // worth more than a weak secret discovered in production.
      throw new IllegalStateException(
          "alertabairro.jwt.segredo (or JWT_SECRET) needs at least " + MIN_SECRET_LENGTH
              + " characters. Generate one with: openssl rand -hex 48");
    }
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withSecretKey(key)
        // Pinning the algorithm: otherwise a token advertising a different one in its
        // header could be accepted.
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(BCRYPT_COST);
  }

  /** Turns the {@code perfil} claim into ROLE_<PERFIL>, so @PreAuthorize can use it. */
  @Bean
  JwtAuthenticationConverter authoritiesConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      String role = jwt.getClaimAsString("perfil");
      return role == null
          ? List.of()
          : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    });
    return converter;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter)
      throws Exception {
    return http
        // The token travels in a header, never a cookie, so there is no session to forge.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(authz -> authz
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(PUBLIC_PATHS).permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, e) ->
                write(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "token ausente, invalido ou expirado"))
            .accessDeniedHandler((req, res, e) ->
                write(res, HttpServletResponse.SC_FORBIDDEN,
                    "acao nao permitida para o seu perfil")))
        .build();
  }

  private static void write(HttpServletResponse res, int status, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.getWriter().write("{\"erro\":\"" + message + "\"}");
  }

  /** Open CORS for Expo Web. Auth travels in a header, so this exposes nobody's session. */
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(List.of("*"));
    cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
