package br.com.fiap.alertabairro.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** The user behind the request's token. Resolved as a controller argument by WebConfig. */
public record AuthenticatedUser(long id, String role) {

  public static AuthenticatedUser from(Jwt jwt) {
    return new AuthenticatedUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("perfil"));
  }

  public boolean isVolunteer() {
    return "voluntario".equals(role);
  }
}
