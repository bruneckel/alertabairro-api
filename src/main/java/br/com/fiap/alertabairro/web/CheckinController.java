package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.security.AuthenticatedUser;
import br.com.fiap.alertabairro.service.CheckinService;
import br.com.fiap.alertabairro.web.dto.CheckinRequest;
import br.com.fiap.alertabairro.web.dto.CheckinResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Estou bem" and the SOS button. */
@RestController
@RequestMapping("/checkins")
@Tag(name = "Check-ins", description = "\"Estou bem\" e \"preciso de ajuda\".")
public class CheckinController {

  private final CheckinService checkins;

  public CheckinController(CheckinService checkins) {
    this.checkins = checkins;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registra o check-in do usuario")
  public CheckinResponse register(
      @Valid @RequestBody CheckinRequest request, AuthenticatedUser current) {
    return checkins.register(current.id(), request);
  }
}
