package br.com.fiap.alertabairro.web.dto;

/** Resposta de cadastro e login, no formato que {@code api.ts} espera. */
public record SessaoResponse(String token, UsuarioResponse usuario) {}
