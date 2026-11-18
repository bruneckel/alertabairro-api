package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.security.AuthenticatedUser;
import br.com.fiap.alertabairro.service.Coordinate;
import br.com.fiap.alertabairro.service.HelpService;
import br.com.fiap.alertabairro.web.dto.ClosedCountResponse;
import br.com.fiap.alertabairro.web.dto.CloseOldRequest;
import br.com.fiap.alertabairro.web.dto.HelpActionRequest;
import br.com.fiap.alertabairro.web.dto.HelpRequestResponse;
import br.com.fiap.alertabairro.web.dto.NewHelpRequest;
import br.com.fiap.alertabairro.web.dto.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Feature 3: the neighbourhood help network. */
@RestController
@RequestMapping("/ajuda")
@Tag(name = "Ajuda", description = "Pedidos e ofertas de ajuda entre vizinhos.")
public class HelpController {

  private final HelpService help;

  public HelpController(HelpService help) {
    this.help = help;
  }

  @GetMapping
  @Operation(summary = "Lista pedidos e ofertas, aguardando primeiro")
  public List<HelpRequestResponse> list(
      @RequestParam(required = false) Integer limite,
      @RequestParam(required = false) Integer deslocamento,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng,
      AuthenticatedUser current) {
    if (lat != null || lng != null) {
      Coordinate.requireValid(
          lat == null ? Double.NaN : lat, lng == null ? Double.NaN : lng);
    }
    return help.list(Pagination.of(limite, deslocamento), current.id(), lat, lng);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cria um pedido ou uma oferta de ajuda")
  public HelpRequestResponse create(
      @Valid @RequestBody NewHelpRequest request, AuthenticatedUser current) {
    return help.create(current.id(), request);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Marca como resolvido, indo para o Historico (so o autor)")
  public HelpRequestResponse resolve(
      @PathVariable long id,
      @Valid @RequestBody HelpActionRequest request,
      AuthenticatedUser current) {
    if (!"resolver".equals(request.action())) {
      throw new IllegalArgumentException("acao deve ser: resolver");
    }
    return help.resolve(id, current.id());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Exclui o pedido ou a oferta (so o autor)")
  public void delete(@PathVariable long id, AuthenticatedUser current) {
    help.delete(id, current.id());
  }

  @PostMapping("/encerrar-antigos")
  @Operation(summary = "Encerra pedidos antigos em lote (prc_encerrar_pedidos_antigos)")
  public ClosedCountResponse closeOld(@Valid @RequestBody CloseOldRequest request) {
    return new ClosedCountResponse(help.closeOlderThan(request.daysOrDefault()));
  }
}
