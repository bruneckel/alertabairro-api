package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * There is no latitude, longitude or author id here. Where someone asking for help is
 * standing never leaves the server; the app only needs how far away they are.
 */
public record HelpRequestResponse(
    long id,
    @JsonProperty("modo") String mode,
    @JsonProperty("categoria") String category,
    @JsonProperty("descricao") String description,
    @JsonProperty("urgencia") String urgency,
    String status,
    @JsonProperty("distancia") String distance,
    @JsonProperty("meu") boolean mine) {}
