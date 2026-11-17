package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One row out of vw_indicadores: 12 aggregations over 4 tables. */
public record IndicatorsResponse(
    @JsonProperty("ocorrenciasAbertas") int openIncidents,
    @JsonProperty("ocorrenciasEmAtendimento") int incidentsInProgress,
    @JsonProperty("ocorrenciasResolvidas") int resolvedIncidents,
    @JsonProperty("ocorrenciasCriticas") int criticalIncidents,
    @JsonProperty("capacidadeAbrigos") int shelterCapacity,
    @JsonProperty("vagasOcupadas") int occupiedSpots,
    @JsonProperty("ocupacaoAbrigosPct") double shelterOccupancyPct,
    @JsonProperty("pedidosAguardando") int waitingRequests,
    @JsonProperty("pedidosAtendidos") int attendedRequests,
    @JsonProperty("pedidosResolvidos") int resolvedRequests,
    @JsonProperty("checkinsEstouBem24h") int wellCheckins24h,
    @JsonProperty("checkinsPrecisoAjuda24h") int helpCheckins24h) {}
