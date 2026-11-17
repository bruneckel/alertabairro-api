package br.com.fiap.alertabairro.repository;

import br.com.fiap.alertabairro.web.dto.UserResponse;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * All SQL uses named bind variables and an explicit column list. {@code senha_hash} is
 * projected only by the authentication query, which is the only one that needs it.
 */
@Repository
public class UserRepository {

  /** Carries the hash, for authentication only. */
  public record UserWithHash(
      long id, String name, String email, String neighborhood, String role, String passwordHash) {

    public UserResponse withoutHash() {
      return new UserResponse(id, name, email, neighborhood, role);
    }
  }

  private final JdbcClient jdbc;

  public UserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Case-insensitive: the functional index uk_usuario_email_lower serves this query. */
  public Optional<UserWithHash> findByEmail(String email) {
    return jdbc.sql("""
            SELECT id, nome, email, bairro, perfil, senha_hash
              FROM usuario
             WHERE LOWER(email) = LOWER(:email)
            """)
        .param("email", email)
        .query((rs, row) -> new UserWithHash(
            rs.getLong("id"), rs.getString("nome"), rs.getString("email"),
            rs.getString("bairro"), rs.getString("perfil"), rs.getString("senha_hash")))
        .optional();
  }

  /**
   * Takes the id from the sequence in its own query rather than from
   * {@code getGeneratedKeys()}: on Oracle, generated keys require telling the driver the
   * column name, otherwise it returns the ROWID. The PK trigger only fires when the id
   * arrives null, so supplying it here is allowed.
   */
  public UserResponse create(
      String name, String email, String neighborhood, String role, String passwordHash) {

    long id = jdbc.sql("SELECT seq_usuario.NEXTVAL FROM dual").query(Long.class).single();

    jdbc.sql("""
            INSERT INTO usuario (id, nome, email, senha_hash, bairro, perfil)
                 VALUES (:id, :name, :email, :hash, :neighborhood, :role)
            """)
        .param("id", id)
        .param("name", name)
        .param("email", email)
        .param("hash", passwordHash)
        .param("neighborhood", neighborhood)
        .param("role", role)
        .update();

    return new UserResponse(id, name, email, neighborhood, role);
  }
}
