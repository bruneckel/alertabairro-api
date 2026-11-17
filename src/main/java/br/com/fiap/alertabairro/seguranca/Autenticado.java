package br.com.fiap.alertabairro.seguranca;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * O usuario por tras do token da requisicao.
 *
 * <p>Os controllers recebem isto como parametro (ver {@code ResolvedorDeAutenticado}) em vez
 * de mexer no SecurityContext: a camada web nao precisa saber como a autenticacao chegou.
 */
public record Autenticado(long id, String perfil) {

  public static Autenticado de(Jwt jwt) {
    return new Autenticado(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("perfil"));
  }

  public boolean voluntario() {
    return "voluntario".equals(perfil);
  }
}
