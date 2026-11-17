package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.security.AuthenticatedUser;
import br.com.fiap.alertabairro.service.HelpService;
import br.com.fiap.alertabairro.web.dto.HelpRequestResponse;
import br.com.fiap.alertabairro.web.dto.NewHelpRequest;
import br.com.fiap.alertabairro.web.dto.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    return help.list(Pagination.of(limite, deslocamento), current.id(), lat, lng);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cria um pedido ou uma oferta de ajuda")
  public HelpRequestResponse create(
      @Valid @RequestBody NewHelpRequest request, AuthenticatedUser current) {
    return help.create(current.id(), request, request.latitude(), request.longitude());
  }
}
