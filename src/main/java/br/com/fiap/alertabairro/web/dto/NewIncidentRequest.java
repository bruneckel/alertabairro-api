package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NewIncidentRequest(
    @JsonProperty("tipo")
    @NotBlank(message = "tipo e obrigatorio")
    @Size(max = 50, message = "tipo deve ter no maximo 50 caracteres")
    String type,

    @JsonProperty("descricao")
    @NotBlank(message = "descricao e obrigatoria")
    @Size(max = 500, message = "descricao deve ter no maximo 500 caracteres")
    String description,

    @NotNull(message = "latitude e obrigatoria")
    @DecimalMin(value = "-90", message = "latitude deve estar entre -90 e 90")
    @DecimalMax(value = "90", message = "latitude deve estar entre -90 e 90")
    Double latitude,

    @NotNull(message = "longitude e obrigatoria")
    @DecimalMin(value = "-180", message = "longitude deve estar entre -180 e 180")
    @DecimalMax(value = "180", message = "longitude deve estar entre -180 e 180")
    Double longitude,

    // The app sends the photo's path on the device: the API stores the text, not the file.
    @JsonProperty("fotoUri")
    @Size(max = 500, message = "fotoUri deve ter no maximo 500 caracteres")
    String photoUri) {

  public NewIncidentRequest {
    type = type == null ? null : type.trim();
    description = description == null ? null : description.trim();
  }
}
