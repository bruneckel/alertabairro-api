package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /abrigos/{id}/vagas}. Positive means people arriving. */
public record VacancyRequest(
    @JsonProperty("quantidade")
    @NotNull(message = "quantidade e obrigatoria")
    @Min(value = -1000, message = "quantidade deve estar entre -1000 e 1000")
    @Max(value = 1000, message = "quantidade deve estar entre -1000 e 1000")
    Integer amount) {

  /** Zero would be a no-op that still took a lock, so the API refuses it. */
  public boolean isZero() {
    return amount != null && amount == 0;
  }
}
