package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.service.ShelterService;
import br.com.fiap.alertabairro.web.dto.ShelterResponse;
import br.com.fiap.alertabairro.web.dto.VacancyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Feature 2: shelters, route to the nearest one and occupancy control. */
@RestController
@RequestMapping("/abrigos")
@Tag(name = "Abrigos", description = "Abrigos, abrigo com vaga mais proximo e controle de vagas.")
public class ShelterController {

  private final ShelterService shelters;

  public ShelterController(ShelterService shelters) {
    this.shelters = shelters;
  }

  @GetMapping
  @Operation(summary = "Lista os abrigos em ordem de nome")
  public List<ShelterResponse> list() {
    return shelters.list();
  }

  @GetMapping("/proximo")
  @Operation(summary = "Abrigo com vaga mais proximo (fn_abrigo_mais_proximo)")
  public ShelterResponse nearest(@RequestParam double lat, @RequestParam double lng) {
    checkCoordinate(lat, lng);
    return shelters.nearest(lat, lng);
  }

  @PatchMapping("/{id}/vagas")
  @Operation(summary = "Entrada e saida de pessoas (prc_controle_vagas, so voluntario)")
  public ShelterResponse changeOccupancy(
      @PathVariable long id, @Valid @RequestBody VacancyRequest request) {
    if (request.isZero()) {
      throw new IllegalArgumentException("quantidade deve ser diferente de 0");
    }
    return shelters.changeOccupancy(id, request.amount());
  }

  /** Throws IllegalArgumentException, which the handler turns into 400 rather than 500. */
  private static void checkCoordinate(double latitude, double longitude) {
    if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("latitude deve estar entre -90 e 90");
    }
    if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("longitude deve estar entre -180 e 180");
    }
  }
}
