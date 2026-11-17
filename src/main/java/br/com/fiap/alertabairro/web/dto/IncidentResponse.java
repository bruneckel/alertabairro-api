package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * There is no author-id field: the API answers {@code meu} instead, which is all the
 * screens need, and the author's id never leaves the server.
 *
 * <p>{@code createdAt} is an {@link OffsetDateTime} because the app feeds the value
 * straight into {@code new Date()} — without an offset the "X min ago" label lies.
 */
public record IncidentResponse(
    long id,
    @JsonProperty("tipo") String type,
    @JsonProperty("descricao") String description,
    double latitude,
    double longitude,
    String status,
    @JsonProperty("prioridade") String priority,
    @JsonProperty("relatos") int reports,
    @JsonProperty("bairro") String neighborhood,
    @JsonProperty("fotoUri") String photoUri,
    @JsonProperty("criadaEm") OffsetDateTime createdAt,
    @JsonProperty("meu") boolean mine) {}
