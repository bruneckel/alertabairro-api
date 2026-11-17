package br.com.fiap.alertabairro.seguranca;

import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emissao do JWT de sessao.
 *
 * <p>Assinatura, expiracao e algoritmo ficam com a biblioteca (Nimbus, via
 * {@link JwtEncoder}); aqui so se decide o conteudo. A validacao na entrada e simetrica,
 * feita pelo decoder configurado em {@code SecurityConfig} -- nenhum dos dois lados e
 * codigo nosso, o que e o ponto.
 */
@Service
public class TokenService {

  static final Duration VALIDADE = Duration.ofHours(8);
  private static final String EMISSOR = "alertabairro-api";

  private final JwtEncoder encoder;

  public TokenService(JwtEncoder encoder) {
    this.encoder = encoder;
  }

  /** Token com {@code sub} = id do usuario e a claim {@code perfil}, valido por 8 horas. */
  public String gerar(long idUsuario, String perfil) {
    Instant agora = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(EMISSOR)
        .subject(String.valueOf(idUsuario))
        .claim("perfil", perfil)
        .issuedAt(agora)
        .expiresAt(agora.plus(VALIDADE))
        .build();
    return encoder
        .encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
