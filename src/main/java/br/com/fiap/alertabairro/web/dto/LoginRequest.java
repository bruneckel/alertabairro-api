package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "informe o e-mail") String email,
    @JsonProperty("senha") @NotBlank(message = "informe a senha") String password) {

  public LoginRequest {
    email = email == null ? null : email.trim().toLowerCase();
  }
}
