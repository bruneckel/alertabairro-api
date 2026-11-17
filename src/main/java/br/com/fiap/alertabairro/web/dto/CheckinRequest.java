package br.com.fiap.alertabairro.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

/** Body of {@code POST /checkins}. The coordinate is optional. */
public record CheckinRequest(
    @Pattern(regexp = "estou_bem|preciso_de_ajuda",
             message = "status deve ser: estou_bem, preciso_de_ajuda")
    String status,

    @DecimalMin(value = "-90", message = "latitude deve estar entre -90 e 90")
    @DecimalMax(value = "90", message = "latitude deve estar entre -90 e 90")
    Double latitude,

    @DecimalMin(value = "-180", message = "longitude deve estar entre -180 e 180")
    @DecimalMax(value = "180", message = "longitude deve estar entre -180 e 180")
    Double longitude) {}
