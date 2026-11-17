package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShelterResponse(
    long id,
    @JsonProperty("nome") String name,
    @JsonProperty("endereco") String address,
    double latitude,
    double longitude,
    @JsonProperty("capacidade") int capacity,
    @JsonProperty("vagasOcupadas") int occupiedSpots) {}
