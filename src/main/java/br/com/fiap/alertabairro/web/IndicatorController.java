package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.service.IndicatorService;
import br.com.fiap.alertabairro.web.dto.IndicatorsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard fed by vw_indicadores. */
@RestController
@RequestMapping("/indicadores")
@Tag(name = "Indicadores", description = "Painel de ocorrencias, abrigos, pedidos e check-ins.")
public class IndicatorController {

  private final IndicatorService indicators;

  public IndicatorController(IndicatorService indicators) {
    this.indicators = indicators;
  }

  @GetMapping
  @Operation(summary = "Indicadores agregados (vw_indicadores)")
  public IndicatorsResponse read() {
    return indicators.read();
  }
}
