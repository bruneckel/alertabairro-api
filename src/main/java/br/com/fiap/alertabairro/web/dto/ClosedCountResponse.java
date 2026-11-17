package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClosedCountResponse(@JsonProperty("encerrados") int closed) {}
