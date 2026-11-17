package br.com.fiap.alertabairro.service;

import br.com.fiap.alertabairro.repository.UsuarioRepository;
import br.com.fiap.alertabairro.repository.UsuarioRepository.UsuarioComHash;
import br.com.fiap.alertabairro.seguranca.TokenService;
import br.com.fiap.alertabairro.web.ErroDeNegocio.ConflitoException;
import br.com.fiap.alertabairro.web.ErroDeNegocio.CredenciaisInvalidasException;
import br.com.fiap.alertabairro.web.dto.CadastroRequest;
import br.com.fiap.alertabairro.web.dto.LoginRequest;
import br.com.fiap.alertabairro.web.dto.SessaoResponse;
import br.com.fiap.alertabairro.web.dto.UsuarioResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro e autenticacao. */
@Service
public class UsuarioService {

  private final UsuarioRepository repositorio;
  private final PasswordEncoder encoder;
  private final TokenService tokens;

  /**
   * Hash descartavel, usado quando o e-mail nao existe.
   *
   * <p>Sem ele, o login responderia na hora para e-mail inexistente e depois de um bcrypt
   * para senha errada: a diferenca de tempo diria a um estranho quem tem conta. Comparar
   * sempre contra algum hash iguala os dois caminhos.
   */
  private final String hashDescartavel;

  public UsuarioService(
      UsuarioRepository repositorio, PasswordEncoder encoder, TokenService tokens) {
    this.repositorio = repositorio;
    this.encoder = encoder;
    this.tokens = tokens;
    this.hashDescartavel = encoder.encode("senha-que-nao-pertence-a-ninguem");
  }

  @Transactional
  public SessaoResponse cadastrar(CadastroRequest pedido) {
    String email = pedido.email();

    if (repositorio.buscarPorEmail(email).isPresent()) {
      throw new ConflitoException("e-mail ja cadastrado");
    }

    UsuarioResponse usuario;
    try {
      usuario = repositorio.criar(
          pedido.nome(), email, pedido.bairro(), pedido.perfil(),
          encoder.encode(pedido.senha()));
    } catch (DuplicateKeyException corrida) {
      // Dois cadastros simultaneos com o mesmo e-mail: a checagem acima passa
      // nos dois e quem decide e a unicidade do banco.
      throw new ConflitoException("e-mail ja cadastrado");
    }

    return new SessaoResponse(tokens.gerar(usuario.id(), usuario.perfil()), usuario);
  }

  @Transactional(readOnly = true)
  public SessaoResponse autenticar(LoginRequest pedido) {
    UsuarioComHash encontrado =
        repositorio.buscarPorEmail(pedido.email()).orElse(null);

    String hash = encontrado == null ? hashDescartavel : encontrado.senhaHash();
    boolean senhaConfere = encoder.matches(pedido.senha(), hash);

    if (encontrado == null || !senhaConfere) {
      throw new CredenciaisInvalidasException();
    }

    UsuarioResponse usuario = encontrado.semHash();
    return new SessaoResponse(tokens.gerar(usuario.id(), usuario.perfil()), usuario);
  }
}
