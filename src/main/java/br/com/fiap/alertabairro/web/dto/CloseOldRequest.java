package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body of {@code POST /ajuda/encerrar-antigos}. Omitting the field means 7 days. */
public record CloseOldRequest(
    @JsonProperty("dias")
    @Min(value = 1, message = "dias deve ser inteiro de 1 a 365")
    @Max(value = 365, message = "dias deve ser inteiro de 1 a 365")
    Integer days) {

  public static final int DEFAULT_DAYS = 7;

  public int daysOrDefault() {
    return days == null ? DEFAULT_DAYS : days;
  }
}
