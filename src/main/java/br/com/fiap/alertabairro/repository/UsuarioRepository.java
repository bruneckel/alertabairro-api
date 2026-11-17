package br.com.fiap.alertabairro.repository;

import br.com.fiap.alertabairro.web.dto.UsuarioResponse;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso a tabela USUARIO.
 *
 * <p>Todo SQL usa bind variables nomeadas: nenhum valor e concatenado na consulta.
 * As colunas sao projetadas explicitamente -- nunca {@code SELECT *} -- e {@code senha_hash}
 * so aparece na consulta de autenticacao, que e a unica que precisa dele.
 */
@Repository
public class UsuarioRepository {

  /** Usuario com o hash, para uso exclusivo da autenticacao. */
  public record UsuarioComHash(
      long id, String nome, String email, String bairro, String perfil, String senhaHash) {

    public UsuarioResponse semHash() {
      return new UsuarioResponse(id, nome, email, bairro, perfil);
    }
  }

  private final JdbcClient jdbc;

  public UsuarioRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Busca ignorando a caixa do e-mail. O indice funcional {@code uk_usuario_email_lower}
   * atende esta consulta, entao o {@code LOWER()} nao custa varredura.
   */
  public Optional<UsuarioComHash> buscarPorEmail(String email) {
    return jdbc.sql("""
            SELECT id, nome, email, bairro, perfil, senha_hash
              FROM usuario
             WHERE LOWER(email) = LOWER(:email)
            """)
        .param("email", email)
        .query((rs, linha) -> new UsuarioComHash(
            rs.getLong("id"), rs.getString("nome"), rs.getString("email"),
            rs.getString("bairro"), rs.getString("perfil"), rs.getString("senha_hash")))
        .optional();
  }

  /**
   * Cria o usuario e devolve-o com o id gerado.
   *
   * <p>O id vem da sequence numa consulta propria, e nao de {@code getGeneratedKeys()}:
   * no Oracle, chaves geradas exigem informar o nome da coluna ao driver, senao o retorno
   * e o ROWID. Pegar o {@code NEXTVAL} antes custa uma ida a mais no cadastro -- operacao
   * rara -- e funciona igual em qualquer versao. O trigger de PK so age quando o id vem
   * nulo, entao informa-lo aqui e permitido.
   */
  public UsuarioResponse criar(
      String nome, String email, String bairro, String perfil, String senhaHash) {

    long id = jdbc.sql("SELECT seq_usuario.NEXTVAL FROM dual").query(Long.class).single();

    jdbc.sql("""
            INSERT INTO usuario (id, nome, email, senha_hash, bairro, perfil)
                 VALUES (:id, :nome, :email, :senhaHash, :bairro, :perfil)
            """)
        .param("id", id)
        .param("nome", nome)
        .param("email", email)
        .param("senhaHash", senhaHash)
        .param("bairro", bairro)
        .param("perfil", perfil)
        .update();

    return new UsuarioResponse(id, nome, email, bairro, perfil);
  }
}
