package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

public record HelpActionRequest(
    @JsonProperty("acao")
    @Pattern(regexp = "resolver", message = "acao deve ser: resolver")
    String action) {}
