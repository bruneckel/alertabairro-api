package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HelpActionRequest(
    @JsonProperty("acao")
    @NotBlank(message = "acao e obrigatoria")
    @Pattern(regexp = "resolver", message = "acao deve ser: resolver")
    String action) {}
