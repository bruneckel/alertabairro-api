package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
    @JsonProperty("nome")
    @NotBlank(message = "nome e obrigatorio")
    @Size(max = 100, message = "nome deve ter no maximo 100 caracteres")
    String name,

    @NotBlank(message = "e-mail e obrigatorio")
    @Email(message = "e-mail invalido")
    @Size(max = 150, message = "e-mail deve ter no maximo 150 caracteres")
    String email,

    @JsonProperty("bairro")
    @NotBlank(message = "bairro e obrigatorio")
    @Size(max = 100, message = "bairro deve ter no maximo 100 caracteres")
    String neighborhood,

    @JsonProperty("senha")
    @NotBlank(message = "senha e obrigatoria")
    @Size(min = 6, max = 72, message = "senha deve ter de 6 a 72 caracteres")
    String password,

    @JsonProperty("perfil")
    @NotBlank(message = "perfil e obrigatorio")
    @Pattern(regexp = "morador|idoso|voluntario",
             message = "perfil deve ser: morador, idoso, voluntario")
    String role) {

  /**
   * Normalizes on construction, not in a helper. Bean Validation runs after Jackson builds
   * the record, so trimming here is what lets {@code @Email} see the clean value: otherwise
   * a valid e-mail with surrounding spaces is rejected before anything can trim it.
   */
  public SignUpRequest {
    name = name == null ? null : name.trim();
    email = email == null ? null : email.trim().toLowerCase();
    neighborhood = neighborhood == null ? null : neighborhood.trim();
  }
}
