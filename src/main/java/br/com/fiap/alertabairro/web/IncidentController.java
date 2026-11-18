package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.security.AuthenticatedUser;
import br.com.fiap.alertabairro.service.IncidentService;
import br.com.fiap.alertabairro.web.dto.IncidentResponse;
import br.com.fiap.alertabairro.web.dto.IncidentActionRequest;
import br.com.fiap.alertabairro.web.dto.NewIncidentRequest;
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

/** Feature 1: the neighbourhood's incident map. */
@RestController
@RequestMapping("/ocorrencias")
@Tag(name = "Ocorrencias", description = "Registro e acompanhamento de ocorrencias do bairro.")
public class IncidentController {

  private final IncidentService incidents;

  public IncidentController(IncidentService incidents) {
    this.incidents = incidents;
  }

  @GetMapping
  @Operation(summary = "Lista as ocorrencias, da mais recente para a mais antiga")
  public List<IncidentResponse> list(
      @RequestParam(required = false) Integer limite,
      @RequestParam(required = false) Integer deslocamento,
      AuthenticatedUser current) {
    return incidents.list(Pagination.of(limite, deslocamento), current.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registra uma ocorrencia (prc_registrar_ocorrencia)")
  public IncidentResponse register(
      @Valid @RequestBody NewIncidentRequest request, AuthenticatedUser current) {
    return incidents.register(current.id(), request);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "confirmar (qualquer perfil), atender e resolver (so voluntario)")
  public IncidentResponse apply(
      @PathVariable long id,
      @Valid @RequestBody IncidentActionRequest request,
      AuthenticatedUser current) {
    return switch (request.action()) {
      case null -> throw new IllegalArgumentException("acao e obrigatoria");
      case "confirmar" -> incidents.confirm(id, current);
      case "atender" -> incidents.attend(id, current);
      case "resolver" -> incidents.resolve(id, current);
      default -> throw new IllegalArgumentException("acao deve ser: confirmar, atender, resolver");
    };
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Exclui a ocorrencia (so o autor)")
  public void delete(@PathVariable long id, AuthenticatedUser current) {
    incidents.delete(id, current);
  }
}
