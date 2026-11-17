package br.com.fiap.alertabairro.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /auth/login}. */
public record LoginRequest(
    @NotBlank(message = "informe o e-mail") String email,
    @NotBlank(message = "informe a senha") String senha) {

  /** Mesma normalizacao do cadastro, para o login aceitar o e-mail em qualquer caixa. */
  public LoginRequest {
    email = email == null ? null : email.trim().toLowerCase();
  }
}
