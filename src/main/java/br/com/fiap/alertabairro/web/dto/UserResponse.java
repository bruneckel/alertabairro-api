package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** No password field exists here, so the hash has nowhere to leak through. */
public record UserResponse(
    long id,
    @JsonProperty("nome") String name,
    String email,
    @JsonProperty("bairro") String neighborhood,
    @JsonProperty("perfil") String role) {}
