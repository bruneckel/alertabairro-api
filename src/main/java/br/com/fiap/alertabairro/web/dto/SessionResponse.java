package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionResponse(String token, @JsonProperty("usuario") UserResponse user) {}
