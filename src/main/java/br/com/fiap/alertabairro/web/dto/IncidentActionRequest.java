package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

/** Body of {@code PATCH /ocorrencias/{id}}. */
public record IncidentActionRequest(
    @JsonProperty("acao")
    @Pattern(regexp = "confirmar|atender|resolver",
             message = "acao deve ser: confirmar, atender, resolver")
    String action) {}
