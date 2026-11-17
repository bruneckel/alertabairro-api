package br.com.fiap.alertabairro.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
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

/**
 * Autenticacao e autorizacao.
 *
 * <p>O JWT e validado por {@link NimbusJwtDecoder}: assinatura, expiracao e algoritmo fixo
 * HS256 sao verificados pela biblioteca, nao por codigo nosso. A claim {@code perfil} vira
 * uma authority {@code ROLE_<PERFIL>}, o que permite exigir voluntario com
 * {@code @PreAuthorize} em vez de um {@code if} repetido em cada handler.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /** Custo 10, igual ao do back-end anterior: os hashes existentes continuam validos. */
  private static final int CUSTO_BCRYPT = 10;

  private static final int MINIMO_SEGREDO = 32;

  private static final String[] PUBLICAS = {
      "/auth/**", "/actuator/health", "/actuator/health/**",
      "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
  };

  private final SecretKey chave;

  public SecurityConfig(
      @Value("${alertabairro.jwt.segredo:${JWT_SECRET:}}") String segredo) {
    if (segredo == null || segredo.length() < MINIMO_SEGREDO) {
      // Falha na subida, e nao na primeira requisicao: um segredo fraco
      // descoberto em producao vale menos do que um container que nao sobe.
      throw new IllegalStateException(
          "alertabairro.jwt.segredo (ou JWT_SECRET) precisa ter no minimo "
              + MINIMO_SEGREDO + " caracteres. Gere com: openssl rand -hex 48");
    }
    this.chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
  }

  @Bean
  JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withSecretKey(chave)
        // Algoritmo fixo: sem isto, um token anunciando outro algoritmo no
        // cabecalho poderia ser aceito.
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(CUSTO_BCRYPT);
  }

  @Bean
  JwtAuthenticationConverter conversorDeAutoridades() {
    JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
    conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
      String perfil = jwt.getClaimAsString("perfil");
      return perfil == null
          ? List.of()
          : List.of(new SimpleGrantedAuthority("ROLE_" + perfil.toUpperCase()));
    });
    return conversor;
  }

  @Bean
  SecurityFilterChain filtros(HttpSecurity http, JwtAuthenticationConverter conversor)
      throws Exception {
    return http
        // Token vai em cabecalho, nunca em cookie: nao ha sessao para forjar.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(autz -> autz
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(PUBLICAS).permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth
            .jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)))
        .exceptionHandling(ex -> ex
            // Mesmo formato {"erro": ...} do resto da API: o app tem um unico
            // caminho de leitura de erro.
            .authenticationEntryPoint((req, res, e) ->
                escrever(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "token ausente, invalido ou expirado"))
            .accessDeniedHandler((req, res, e) ->
                escrever(res, HttpServletResponse.SC_FORBIDDEN,
                    "acao nao permitida para o seu perfil")))
        .build();
  }

  private static void escrever(HttpServletResponse res, int status, String mensagem)
      throws java.io.IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.getWriter().write("{\"erro\":\"" + mensagem + "\"}");
  }

  /**
   * CORS liberado para o Expo Web. O app nativo nao precisa, e como a autenticacao vai
   * em cabecalho (e nao em cookie), liberar origem nao expoe sessao de ninguem.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(List.of("*"));
    cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
    fonte.registerCorsConfiguration("/**", cfg);
    return fonte;
  }
}
