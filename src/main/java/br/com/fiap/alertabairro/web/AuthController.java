package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.service.UserService;
import br.com.fiap.alertabairro.web.dto.LoginRequest;
import br.com.fiap.alertabairro.web.dto.SessionResponse;
import br.com.fiap.alertabairro.web.dto.SignUpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Entry into the app. Does not count as a feature for the Global Solution. */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticacao", description = "Cadastro e login. Devolvem o token das demais rotas.")
public class AuthController {

  private final UserService users;

  public AuthController(UserService users) {
    this.users = users;
  }

  @PostMapping("/cadastro")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cria a conta e devolve a sessao")
  public SessionResponse signUp(@Valid @RequestBody SignUpRequest request) {
    return users.signUp(request);
  }

  @PostMapping("/login")
  @Operation(summary = "Autentica e devolve o token")
  public SessionResponse login(@Valid @RequestBody LoginRequest request) {
    return users.authenticate(request);
  }
}
