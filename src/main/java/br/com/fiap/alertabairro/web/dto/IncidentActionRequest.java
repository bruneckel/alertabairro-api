package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of {@code PATCH /ocorrencias/{id}}. */
public record IncidentActionRequest(
    @JsonProperty("acao")
    // @Pattern deixa null passar por desenho: sem @NotBlank, um corpo {} chegava nulo
    // ao switch do controller e virava NullPointerException, ou seja 500.
    @NotBlank(message = "acao e obrigatoria")
    @Pattern(regexp = "confirmar|atender|resolver",
             message = "acao deve ser: confirmar, atender, resolver")
    String action) {}
