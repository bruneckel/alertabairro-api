package br.com.fiap.alertabairro.web;

import br.com.fiap.alertabairro.service.UsuarioService;
import br.com.fiap.alertabairro.web.dto.CadastroRequest;
import br.com.fiap.alertabairro.web.dto.LoginRequest;
import br.com.fiap.alertabairro.web.dto.SessaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Entrada no aplicativo. Nao conta como funcionalidade para a Global Solution. */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticacao", description = "Cadastro e login. Devolvem o token usado nas demais rotas.")
public class AuthController {

  private final UsuarioService usuarios;

  public AuthController(UsuarioService usuarios) {
    this.usuarios = usuarios;
  }

  @PostMapping("/cadastro")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cria a conta e ja devolve a sessao")
  public SessaoResponse cadastrar(@Valid @RequestBody CadastroRequest pedido) {
    return usuarios.cadastrar(pedido);
  }

  @PostMapping("/login")
  @Operation(summary = "Autentica e devolve o token")
  public SessaoResponse entrar(@Valid @RequestBody LoginRequest pedido) {
    return usuarios.autenticar(pedido);
  }
}
