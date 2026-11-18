package br.com.fiap.alertabairro.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /ajuda}. The coordinate is optional. */
public record NewHelpRequest(
    @JsonProperty("modo")
    @NotBlank(message = "modo e obrigatorio")
    @Pattern(regexp = "pedido|oferta", message = "modo deve ser: pedido, oferta")
    String mode,

    @JsonProperty("categoria")
    @NotBlank(message = "categoria e obrigatoria")
    @Size(max = 50, message = "categoria deve ter no maximo 50 caracteres")
    String category,

    @JsonProperty("descricao")
    @NotBlank(message = "descricao e obrigatoria")
    @Size(max = 500, message = "descricao deve ter no maximo 500 caracteres")
    String description,

    @JsonProperty("urgencia")
    @NotBlank(message = "urgencia e obrigatoria")
    @Pattern(regexp = "baixa|media|alta", message = "urgencia deve ser: baixa, media, alta")
    String urgency,

    @DecimalMin(value = "-90", message = "latitude deve estar entre -90 e 90")
    @DecimalMax(value = "90", message = "latitude deve estar entre -90 e 90")
    Double latitude,

    @DecimalMin(value = "-180", message = "longitude deve estar entre -180 e 180")
    @DecimalMax(value = "180", message = "longitude deve estar entre -180 e 180")
    Double longitude) {

  public NewHelpRequest {
    category = category == null ? null : category.trim();
    description = description == null ? null : description.trim();
  }
}
